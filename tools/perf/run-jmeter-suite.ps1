param(
    [int[]]$ConcurrencyLevels = @(20, 50, 100, 200),
    [int]$DurationSeconds = 20,
    [int]$RampUpSeconds = 5,
    [string]$OutputRoot = "D:\IDEAJAVA\game-community\logs\perf",
    [string[]]$IncludeServices = @(),
    [int]$ConnectTimeoutMillis = 5000,
    [int]$ResponseTimeoutMillis = 5000
)

$ErrorActionPreference = "Stop"

function Get-JwtPayload {
    param([string]$Token)
    $payload = $Token.Split('.')[1].Replace('-', '+').Replace('_', '/')
    while ($payload.Length % 4 -ne 0) {
        $payload += '='
    }
    return ([System.Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($payload)) | ConvertFrom-Json)
}

function Get-Percentile {
    param(
        [double[]]$Values,
        [double]$Percentile
    )
    if (-not $Values -or $Values.Count -eq 0) {
        return 0
    }
    $sorted = $Values | Sort-Object
    $index = [Math]::Ceiling(($Percentile / 100.0) * $sorted.Count) - 1
    if ($index -lt 0) { $index = 0 }
    if ($index -ge $sorted.Count) { $index = $sorted.Count - 1 }
    return [Math]::Round([double]$sorted[$index], 2)
}

function Measure-Jtl {
    param([string]$JtlPath)
    $rows = Import-Csv $JtlPath
    if (-not $rows -or $rows.Count -eq 0) {
        throw "JTL empty: $JtlPath"
    }
    $elapsedValues = @()
    $successCount = 0
    $failCount = 0
    $minStart = [long]::MaxValue
    $maxEnd = 0L
    foreach ($row in $rows) {
        $ts = [long]$row.timeStamp
        $elapsed = [double]$row.elapsed
        $endTs = $ts + [long][Math]::Ceiling($elapsed)
        if ($ts -lt $minStart) { $minStart = $ts }
        if ($endTs -gt $maxEnd) { $maxEnd = $endTs }
        $elapsedValues += $elapsed
        if ($row.success -eq 'true') {
            $successCount++
        } else {
            $failCount++
        }
    }
    $wallSeconds = [Math]::Max((($maxEnd - $minStart) / 1000.0), 0.001)
    $totalCount = $successCount + $failCount
    return [pscustomobject]@{
        TotalCount = $totalCount
        SuccessCount = $successCount
        FailCount = $failCount
        ErrorRate = [Math]::Round(($failCount / [Math]::Max($totalCount, 1)) * 100, 2)
        Qps = [Math]::Round($totalCount / $wallSeconds, 2)
        P90 = Get-Percentile -Values $elapsedValues -Percentile 90
        P99 = Get-Percentile -Values $elapsedValues -Percentile 99
        Max = ($elapsedValues | Measure-Object -Maximum).Maximum
        Min = ($elapsedValues | Measure-Object -Minimum).Minimum
        Avg = [Math]::Round(($elapsedValues | Measure-Object -Average).Average, 2)
    }
}

function Get-BottleneckNote {
    param(
        [string]$Service,
        [string]$Endpoint,
        [pscustomobject[]]$Runs,
        [pscustomobject]$Chosen
    )
    $peak = $Runs | Sort-Object Qps -Descending | Select-Object -First 1
    $next = $Runs | Where-Object { $_.Concurrency -gt $Chosen.Concurrency } | Sort-Object Concurrency | Select-Object -First 1
    $degrade = $null
    if ($next) {
        $degrade = "在并发 $($next.Concurrency) 时 p99 提升到 $($next.P99)ms，误码率 $($next.ErrorRate)%"
    }
    switch ($Service) {
        'user-service' { return "主要受 MySQL 单行查询和会话附带逻辑限制。$degrade" }
        'content-service' { return "主要受 MySQL 分页查询与排序限制，页查越深越明显。$degrade" }
        'social-service' { return "主要受跨服务取文章信息和社交统计写放大限制。$degrade" }
        'notification-service' { return "主要受 MySQL 未读状态查询与 SSE 汇总查询限制。$degrade" }
        'recommend-service' { return "主要受 Redis 热榜读取与内容/用户信息补全的远程查询限制。$degrade" }
        'audit-service' { return "主要受举报分页查库和详情补全限制。$degrade" }
        'game-account-service' { return "主要受 MySQL 图鉴分页查询与拥有状态关联判断限制。$degrade" }
        'shop-service' { return "主要受商品列表查库与 Redisson/Redis 初始化访问限制。$degrade" }
        'search-service' { return "主要受 Elasticsearch 查询与结果组装限制。$degrade" }
        default { return "主要受数据库/缓存与远程依赖综合限制。$degrade" }
    }
}

$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$jmeterJava = "D:\JAVAINSTALL\jdk17\bin\java.exe"
$jmeterJar = "D:\apache-jmeter-5.6.3\apache-jmeter-5.6.3\bin\ApacheJMeter.jar"
$jmx = Join-Path $PSScriptRoot "http-single-endpoint.jmx"
if (-not (Test-Path $jmeterJava)) {
    throw "Java not found: $jmeterJava"
}
if (-not (Test-Path $jmeterJar)) {
    throw "JMeter jar not found: $jmeterJar"
}

New-Item -ItemType Directory -Force -Path $OutputRoot | Out-Null

$loginBody = @{ accountId = '10000'; password = '123456' } | ConvertTo-Json
$login = Invoke-RestMethod -Uri 'http://localhost:8080/user/login/account' -Method Post -ContentType 'application/json' -Body $loginBody
if ($login.code -ne 200) {
    throw "Login failed: $($login | ConvertTo-Json -Depth 6)"
}
$jwt = $login.data.accessToken
$payload = Get-JwtPayload -Token $jwt

$commonProps = @{
    userId = [string]$payload.userId
    userType = [string]$payload.type
    gameAccount = [string]$payload.gameAccount
    sessionId = [string]$payload.sessionId
}

$cases = @(
    @{ Service = 'user-service'; Endpoint = '/user/me'; Port = 8081; Label = 'user-me' },
    @{ Service = 'content-service'; Endpoint = '/article/page?page=1&size=10'; Port = 8082; Label = 'content-page' },
    @{ Service = 'social-service'; Endpoint = '/social/article/10'; Port = 8084; Label = 'social-article-view' },
    @{ Service = 'notification-service'; Endpoint = '/notification/summary'; Port = 8092; Label = 'notification-summary' },
    @{ Service = 'recommend-service'; Endpoint = '/hot-article/list?page=1&size=10'; Port = 8086; Label = 'recommend-hot-list' },
    @{ Service = 'audit-service'; Endpoint = '/audit/moderation/page?page=1&size=10&status=0&taskType=REPORT'; Port = 8091; Label = 'audit-report-page' },
    @{ Service = 'game-account-service'; Endpoint = '/game-account/resources/characters?page=1&size=10'; Port = 8083; Label = 'game-character-catalog' },
    @{ Service = 'shop-service'; Endpoint = '/shop/item/list?page=1&size=10'; Port = 8085; Label = 'shop-item-list' },
    @{ Service = 'search-service'; Endpoint = '/search/article?keyword=test&page=1&size=10'; Port = 8087; Label = 'search-article' }
)

if ($IncludeServices.Count -gt 0) {
    if ($IncludeServices.Count -eq 1 -and $IncludeServices[0] -like '*,*') {
        $IncludeServices = $IncludeServices[0].Split(',') | ForEach-Object { $_.Trim() } | Where-Object { $_ }
    }
    $cases = $cases | Where-Object { $IncludeServices -contains $_.Service }
}

$allRuns = New-Object System.Collections.Generic.List[object]
$chosenRows = New-Object System.Collections.Generic.List[object]

foreach ($case in $cases) {
    Write-Host "Running $($case.Service) $($case.Endpoint)" -ForegroundColor Cyan
    $serviceRuns = @()
    foreach ($concurrency in $ConcurrencyLevels) {
        $safeLabel = "$($case.Service)-$($case.Label)-$concurrency"
        $jtlPath = Join-Path $OutputRoot "$safeLabel.csv"
        $logPath = Join-Path $OutputRoot "$safeLabel.jmeter.log"
        if (Test-Path $jtlPath) { Remove-Item $jtlPath -Force }
        if (Test-Path $logPath) { Remove-Item $logPath -Force }

        $argList = @(
            '-n',
            '-t', $jmx,
            '-l', $jtlPath,
            '-j', $logPath,
            '-Jhost=localhost',
            "-Jport=$($case.Port)",
            '-Jprotocol=http',
            "-Jpath=$($case.Endpoint)",
            '-Jmethod=GET',
            "-Jlabel=$($case.Label)",
            "-Jthreads=$concurrency",
            "-Jrampup=$RampUpSeconds",
            "-Jduration=$DurationSeconds",
            "-JconnectTimeout=$ConnectTimeoutMillis",
            "-JresponseTimeout=$ResponseTimeoutMillis",
            "-JuserId=$($commonProps.userId)",
            "-JuserType=$($commonProps.userType)",
            "-JgameAccount=$($commonProps.gameAccount)",
            "-JsessionId=$($commonProps.sessionId)"
        )
        & $jmeterJava '-jar' $jmeterJar @argList | Out-Null
        $metric = Measure-Jtl -JtlPath $jtlPath
        $run = [pscustomobject]@{
            Service = $case.Service
            Endpoint = $case.Endpoint
            Concurrency = $concurrency
            Qps = $metric.Qps
            P90 = $metric.P90
            P99 = $metric.P99
            Avg = $metric.Avg
            Max = $metric.Max
            ErrorRate = $metric.ErrorRate
            SuccessCount = $metric.SuccessCount
            FailCount = $metric.FailCount
        }
        $serviceRuns += $run
        $allRuns.Add($run) | Out-Null
    }

    $stable = $serviceRuns | Where-Object { $_.ErrorRate -le 1 -and $_.P99 -le 1000 } | Sort-Object Concurrency -Descending | Select-Object -First 1
    if (-not $stable) {
        $stable = $serviceRuns | Sort-Object ErrorRate, P99, Concurrency | Select-Object -First 1
    }
    $note = Get-BottleneckNote -Service $case.Service -Endpoint $case.Endpoint -Runs $serviceRuns -Chosen $stable
    $chosenRows.Add([pscustomobject]@{
        Service = $stable.Service
        Endpoint = $stable.Endpoint
        Concurrency = $stable.Concurrency
        Qps = $stable.Qps
        P90 = $stable.P90
        P99 = $stable.P99
        ErrorRate = $stable.ErrorRate
        Bottleneck = $note
    }) | Out-Null
}

$timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$rawCsv = Join-Path $OutputRoot "perf-raw-$timestamp.csv"
$summaryMd = Join-Path $OutputRoot "perf-report-$timestamp.md"

$allRuns | Export-Csv -Path $rawCsv -NoTypeInformation -Encoding UTF8

$lines = @()
$lines += "# 微服务压测报告"
$lines += ""
$lines += "- 压测工具: Apache JMeter 5.6.3"
$lines += "- 执行时间: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
$lines += "- 并发档位: $($ConcurrencyLevels -join ', ')"
$lines += "- 单档持续时间: ${DurationSeconds}s"
$lines += "- 说明: 结果为直连微服务端口，不包含网关开销"
$lines += ""
$lines += "## 汇总"
$lines += ""
$lines += "| 某微服务 | 某接口 | 并发度 | QPS | p90(ms) | p99(ms) | 错误率 | 受限点 |"
$lines += "| --- | --- | ---: | ---: | ---: | ---: | ---: | --- |"
foreach ($row in $chosenRows) {
    $lines += "| $($row.Service) | $($row.Endpoint) | $($row.Concurrency) | $($row.Qps) | $($row.P90) | $($row.P99) | $($row.ErrorRate)% | $($row.Bottleneck) |"
}
$lines += ""
$lines += "## 原始分档数据"
$lines += ""
foreach ($case in $cases) {
    $lines += "### $($case.Service) $($case.Endpoint)"
    $lines += ""
    $lines += "| 并发度 | QPS | p90(ms) | p99(ms) | avg(ms) | max(ms) | 错误率 |"
    $lines += "| ---: | ---: | ---: | ---: | ---: | ---: | ---: |"
    foreach ($run in ($allRuns | Where-Object { $_.Service -eq $case.Service } | Sort-Object Concurrency)) {
        $lines += "| $($run.Concurrency) | $($run.Qps) | $($run.P90) | $($run.P99) | $($run.Avg) | $($run.Max) | $($run.ErrorRate)% |"
    }
    $lines += ""
}

Set-Content -Path $summaryMd -Value $lines -Encoding UTF8

[pscustomobject]@{
    RawCsv = $rawCsv
    SummaryMd = $summaryMd
} | ConvertTo-Json -Depth 3
