param(
    [string]$InputRoot = "D:\IDEAJAVA\game-community\logs\perf"
)

$ErrorActionPreference = "Stop"

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
        if ($row.success -eq 'true') { $successCount++ } else { $failCount++ }
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
        Avg = [Math]::Round(($elapsedValues | Measure-Object -Average).Average, 2)
        Max = ($elapsedValues | Measure-Object -Maximum).Maximum
    }
}

function Get-BottleneckNote {
    param([string]$Service, [pscustomobject[]]$Runs, [pscustomobject]$Chosen)
    $next = $Runs | Where-Object { $_.Concurrency -gt $Chosen.Concurrency } | Sort-Object Concurrency | Select-Object -First 1
    $degrade = if ($next) { "在并发 $($next.Concurrency) 时 p99 $($next.P99)ms，错误率 $($next.ErrorRate)%" } else { "更高并发样本缺失或未继续施压" }
    switch ($Service) {
        'user-service' { return "MySQL 单用户查询与会话附带读取为主。$degrade" }
        'content-service' { return "MySQL 分页排序与 count 查询为主。$degrade" }
        'social-service' { return "远程拉文章信息 + 社交统计查写为主。$degrade" }
        'notification-service' { return "未读状态查库与 SSE 汇总查询为主。$degrade" }
        'recommend-service' { return "Redis 热榜读取后叠加内容/作者补全，远程调用放大明显。$degrade" }
        'audit-service' { return "举报分页查库和详情补全为主。$degrade" }
        'game-account-service' { return "图鉴分页 + 拥有状态判定关联查询为主。$degrade" }
        'shop-service' { return "商品列表查库与 Redis/Redisson 依赖为主。$degrade" }
        'search-service' { return "Elasticsearch 查询与结果组装为主。$degrade" }
        default { return "数据库/缓存/远程调用综合限制。$degrade" }
    }
}

$rawRuns = New-Object System.Collections.Generic.List[object]
$files = Get-ChildItem $InputRoot -Filter '*.csv' | Where-Object { $_.Name -notlike 'perf-raw-*' }
foreach ($file in $files) {
    if ($file.BaseName -match '^(?<service>.+)-(?<label>.+)-(?<concurrency>\d+)$') {
        $service = $Matches['service']
        $concurrency = [int]$Matches['concurrency']
        $metric = Measure-Jtl -JtlPath $file.FullName
        $endpoint = switch ($service) {
            'user-service' { '/user/me' }
            'content-service' { '/article/page?page=1&size=10' }
            'social-service' { '/social/article/10' }
            'notification-service' { '/notification/summary' }
            'recommend-service' { '/hot-article/list?page=1&size=10' }
            'audit-service' { '/audit/moderation/page?page=1&size=10&status=0&taskType=REPORT' }
            'game-account-service' { '/game-account/resources/characters?page=1&size=10' }
            'shop-service' { '/shop/item/list?page=1&size=10' }
            'search-service' { '/search/article?keyword=test&page=1&size=10' }
            default { '/' }
        }
        $rawRuns.Add([pscustomobject]@{
            Service = $service
            Endpoint = $endpoint
            Concurrency = $concurrency
            Qps = $metric.Qps
            P90 = $metric.P90
            P99 = $metric.P99
            Avg = $metric.Avg
            Max = $metric.Max
            ErrorRate = $metric.ErrorRate
            SuccessCount = $metric.SuccessCount
            FailCount = $metric.FailCount
        }) | Out-Null
    }
}

$timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$rawCsv = Join-Path $InputRoot "perf-raw-$timestamp.csv"
$summaryMd = Join-Path $InputRoot "perf-report-$timestamp.md"

$rawRuns | Sort-Object Service, Concurrency | Export-Csv -Path $rawCsv -NoTypeInformation -Encoding UTF8

$chosenRows = foreach ($group in ($rawRuns | Group-Object Service)) {
    $runs = $group.Group | Sort-Object Concurrency
    $stable = $runs | Where-Object { $_.ErrorRate -le 1 -and $_.P99 -le 1000 } | Sort-Object Concurrency -Descending | Select-Object -First 1
    if (-not $stable) {
        $stable = $runs | Sort-Object ErrorRate, P99, Concurrency | Select-Object -First 1
    }
    [pscustomobject]@{
        Service = $stable.Service
        Endpoint = $stable.Endpoint
        Concurrency = $stable.Concurrency
        Qps = $stable.Qps
        P90 = $stable.P90
        P99 = $stable.P99
        ErrorRate = $stable.ErrorRate
        Bottleneck = Get-BottleneckNote -Service $stable.Service -Runs $runs -Chosen $stable
    }
}

$lines = @()
$lines += "# 微服务压测报告"
$lines += ""
$lines += "- 压测工具: Apache JMeter 5.6.3"
$lines += "- 汇总时间: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
$lines += "- 说明: 结果为直连微服务端口，不包含网关开销"
$lines += ""
$lines += "## 汇总"
$lines += ""
$lines += "| 某微服务 | 某接口 | 并发度 | QPS | p90(ms) | p99(ms) | 错误率 | 受限点 |"
$lines += "| --- | --- | ---: | ---: | ---: | ---: | ---: | --- |"
foreach ($row in ($chosenRows | Sort-Object Service)) {
    $lines += "| $($row.Service) | $($row.Endpoint) | $($row.Concurrency) | $($row.Qps) | $($row.P90) | $($row.P99) | $($row.ErrorRate)% | $($row.Bottleneck) |"
}
$lines += ""
$lines += "## 原始分档数据"
$lines += ""
foreach ($group in ($rawRuns | Group-Object Service | Sort-Object Name)) {
    $endpoint = ($group.Group | Select-Object -First 1).Endpoint
    $lines += "### $($group.Name) $endpoint"
    $lines += ""
    $lines += "| 并发度 | QPS | p90(ms) | p99(ms) | avg(ms) | max(ms) | 错误率 |"
    $lines += "| ---: | ---: | ---: | ---: | ---: | ---: | ---: |"
    foreach ($run in ($group.Group | Sort-Object Concurrency)) {
        $lines += "| $($run.Concurrency) | $($run.Qps) | $($run.P90) | $($run.P99) | $($run.Avg) | $($run.Max) | $($run.ErrorRate)% |"
    }
    $lines += ""
}

Set-Content -Path $summaryMd -Value $lines -Encoding UTF8

[pscustomobject]@{
    RawCsv = $rawCsv
    SummaryMd = $summaryMd
    ServiceCount = ($rawRuns | Group-Object Service).Count
} | ConvertTo-Json -Depth 4
