# 认证接口 curl 测试手册

user-service 认证 API 全场景测试用例。建议**经网关**调用（与前端一致）。

## 前置准备

```bash
# 网关（推荐，与前端 REACT_APP_BASE_URL 一致）
export BASE=http://localhost:8080

# 直连 user-service（跳过网关时用）
# export BASE=http://localhost:8081

export JAR=/tmp/game-auth-cookies.txt
export EMAIL="test-$(date +%s)@qq.com"   # 新注册测试邮箱（需真实可收信域名）
export EXISTING_EMAIL="3100672662@qq.com"  # 已注册邮箱（按你环境修改）
export PASS="abc123"                       # 合法密码：6-10 位字母数字
export WRONG_PASS="wrong1"

# 从 Redis 读取验证码（Docker 容器名默认 redis）
alias redis-get-code='docker exec redis redis-cli GET'
get_code() {
  local biz="${1:-REGISTER}"
  local mail="${2:-$EMAIL}"
  redis-get-code "email:code:${biz}:$(echo "$mail" | tr '[:upper:]' '[:lower:]')"
}

# 清除限流 key（仅本地测试）
clear_email_limits() {
  local mail="${1:-$EMAIL}"
  local lower="$(echo "$mail" | tr '[:upper:]' '[:lower:]')"
  docker exec redis redis-cli DEL \
    "email:cooldown:REGISTER:${lower}" \
    "email:cooldown:RESET_PASSWORD:${lower}" \
    "email:daily:REGISTER:${lower}" \
    "email:daily:RESET_PASSWORD:${lower}" \
    "email:verify:fail:${lower}" \
    "email:verify:lock:${lower}" \
    "email:code:REGISTER:${lower}" \
    "email:code:RESET_PASSWORD:${lower}" >/dev/null
}
```

**统一响应格式**（`Result`）：

```json
{ "code": 200, "message": "success", "data": { ... } }
```

| code | 含义 |
|------|------|
| 200 | 成功 |
| 400 | 参数/业务校验失败 |
| 401 | 未授权（如密码错误） |
| 40102 | refreshToken 失效（`AuthErrorCodes.REFRESH_EXPIRED`） |
| 403 | 禁止（账号封禁/锁定/验证码锁定） |
| 404 | 资源不存在（邮箱未注册） |
| 409 | 冲突（邮箱已注册） |
| 429 | 请求过于频繁 |

---

## 1. POST `/user/auth/send-code` — 发送验证码

### 1.1 ✅ 注册发码成功

```bash
curl -s -X POST "$BASE/user/auth/send-code" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"bizType\":\"REGISTER\"}" | python3 -m json.tool
```

**预期**：

```json
{
  "code": 200,
  "message": "success",
  "data": { "expireIn": 300 }
}
```

- 邮件主题：`游戏社区 - 注册验证码`
- Redis：`email:code:REGISTER:<邮箱小写>` 有 6 位数字

---

### 1.2 ✅ 找回密码发码成功（邮箱须已注册）

```bash
curl -s -X POST "$BASE/user/auth/send-code" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EXISTING_EMAIL\",\"bizType\":\"RESET_PASSWORD\"}" | python3 -m json.tool
```

**预期**：`code=200`，`data.expireIn=300`，邮件主题 `游戏社区 - 找回密码验证码`

---

### 1.3 ❌ 缺少邮箱（@Valid）

```bash
curl -s -X POST "$BASE/user/auth/send-code" \
  -H 'Content-Type: application/json' \
  -d '{}' | python3 -m json.tool
```

**预期**：`code=400`，`message="请输入邮箱"`

---

### 1.4 ❌ 邮箱格式错误（@Valid）

```bash
curl -s -X POST "$BASE/user/auth/send-code" \
  -H 'Content-Type: application/json' \
  -d '{"email":"not-an-email","bizType":"REGISTER"}' | python3 -m json.tool
```

**预期**：`code=400`，`message="请输入正确的邮箱格式"`

---

### 1.5 ❌ 邮箱域名 MX 无效（`EMAIL_MX_CHECK_ENABLED=true` 时）

```bash
curl -s -X POST "$BASE/user/auth/send-code" \
  -H 'Content-Type: application/json' \
  -d '{"email":"user@invalid-domain-xyz123.fake","bizType":"REGISTER"}' | python3 -m json.tool
```

**预期**：`code=400`，`message="邮箱域名无效或无法接收邮件"`

---

### 1.6 ❌ 注册发码 — 邮箱已注册

```bash
curl -s -X POST "$BASE/user/auth/send-code" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EXISTING_EMAIL\",\"bizType\":\"REGISTER\"}" | python3 -m json.tool
```

**预期**：`code=409`，`message="该邮箱已被注册"`

---

### 1.7 ❌ 找回密码发码 — 邮箱未注册

```bash
curl -s -X POST "$BASE/user/auth/send-code" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"nobody-$(date +%s)@qq.com\",\"bizType\":\"RESET_PASSWORD\"}" | python3 -m json.tool
```

**预期**：`code=404`，`message="该邮箱未注册"`

---

### 1.8 ❌ 60 秒内重复发送（冷却）

先成功发一次，**立即**再发：

```bash
curl -s -X POST "$BASE/user/auth/send-code" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"bizType\":\"REGISTER\"}" | python3 -m json.tool
```

**预期**：`code=429`，`message` 含 `发送过于频繁，请`，并带剩余秒数

> 测试前可 `clear_email_limits "$EMAIL"` 重置；或等 60 秒。

---

### 1.9 ❌ 每日发送上限（每邮箱每 bizType 10 次）

连续发码 11 次（需多次清除 cooldown，保留 daily 计数）：

```bash
for i in $(seq 1 11); do
  docker exec redis redis-cli DEL "email:cooldown:REGISTER:$(echo "$EMAIL" | tr '[:upper:]' '[:lower:]')" >/dev/null
  curl -s -X POST "$BASE/user/auth/send-code" -H 'Content-Type: application/json' \
    -d "{\"email\":\"$EMAIL\",\"bizType\":\"REGISTER\"}" | python3 -c "import sys,json; d=json.load(sys.stdin); print(i, d['code'], d['message'])"
done
```

**预期**：第 11 次 `code=429`，`message="今日发送次数已达上限，请明天再试"`

---

### 1.10 ℹ️ bizType 省略或未知值

```bash
curl -s -X POST "$BASE/user/auth/send-code" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\"}" | python3 -m json.tool
```

**预期**：按 `REGISTER` 处理（`bizType` 为空或非 `RESET_PASSWORD` 均视为注册）

---

## 2. POST `/user/auth/register` — 注册

### 2.1 ✅ 注册成功（完整流程）

```bash
clear_email_limits "$EMAIL"

# Step 1: 发码
curl -s -X POST "$BASE/user/auth/send-code" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"bizType\":\"REGISTER\"}" >/dev/null

# Step 2: 取验证码
CODE="$(get_code REGISTER "$EMAIL")"
echo "验证码: $CODE"

# Step 3: 注册
curl -s -X POST "$BASE/user/auth/register" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASS\",\"code\":\"$CODE\"}" | python3 -m json.tool
```

**预期**：

```json
{
  "code": 200,
  "message": "success",
  "data": { "email": "testxxx@qq.com" }
}
```

---

### 2.2 ❌ 验证码错误

```bash
curl -s -X POST "$BASE/user/auth/register" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASS\",\"code\":\"000000\"}" | python3 -m json.tool
```

**预期**：`code=400`，`message="验证码错误"`（须先发过码且未过期）

---

### 2.3 ❌ 验证码过期 / 未获取

```bash
curl -s -X POST "$BASE/user/auth/register" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"fresh-$(date +%s)@qq.com\",\"password\":\"$PASS\",\"code\":\"123456\"}" | python3 -m json.tool
```

**预期**：`code=400`，`message="验证码已过期，请重新获取"`

---

### 2.4 ❌ 密码格式不合法

```bash
curl -s -X POST "$BASE/user/auth/register" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"password\":\"abc\",\"code\":\"123456\"}" | python3 -m json.tool
```

**预期**：`code=400`，`message="密码为 6-10 位数字、字母或特殊字符"`

---

### 2.5 ❌ 验证码非 6 位数字（@Valid）

```bash
curl -s -X POST "$BASE/user/auth/register" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASS\",\"code\":\"12\"}" | python3 -m json.tool
```

**预期**：`code=400`，`message="验证码为 6 位数字"`

---

### 2.6 ❌ 重复注册

对已注册邮箱再次注册（验证码须有效）：

```bash
# 先发码再注册同一 EXISTING_EMAIL
curl -s -X POST "$BASE/user/auth/register" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EXISTING_EMAIL\",\"password\":\"$PASS\",\"code\":\"123456\"}" | python3 -m json.tool
```

**预期**：`code=409`，`message="该邮箱已被注册"`

---

### 2.7 ❌ 验证码连续错误 5 次锁定（30 分钟）

```bash
clear_email_limits "$EMAIL"
curl -s -X POST "$BASE/user/auth/send-code" -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"bizType\":\"REGISTER\"}" >/dev/null

for i in $(seq 1 5); do
  curl -s -X POST "$BASE/user/auth/register" -H 'Content-Type: application/json' \
    -d "{\"email\":\"$EMAIL\",\"password\":\"$PASS\",\"code\":\"000000\"}" | \
    python3 -c "import sys,json; d=json.load(sys.stdin); print(i, d['code'], d['message'])"
done
```

**预期**：前几次 `400 验证码错误`，第 5 次后再次尝试：

`code=403`，`message="验证码验证失败次数过多，请30分钟后重试"`

---

## 3. POST `/user/auth/login` — 登录

### 3.1 ✅ 登录成功

```bash
rm -f "$JAR"
curl -s -X POST "$BASE/user/auth/login" \
  -H 'Content-Type: application/json' \
  -c "$JAR" \
  -d "{\"email\":\"$EXISTING_EMAIL\",\"password\":\"$PASS\"}" | python3 -m json.tool
```

**预期**：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "accessToken": "eyJ...",
    "accessExpiresIn": 1800,
    "user": {
      "id": 1,
      "email": "3100672662@qq.com",
      "nickname": "...",
      "accountId": 10001
    }
  }
}
```

- 响应头 `Set-Cookie` 含 `game_community_refresh_token`（HttpOnly）
- Cookie 保存到 `$JAR`

```bash
# 保存 accessToken 供后续使用
export TOKEN=$(curl -s -X POST "$BASE/user/auth/login" \
  -H 'Content-Type: application/json' -c "$JAR" \
  -d "{\"email\":\"$EXISTING_EMAIL\",\"password\":\"$PASS\"}" | \
  python3 -c "import sys,json; print(json.load(sys.stdin)['data']['accessToken'])")
```

---

### 3.2 ❌ 邮箱未注册

```bash
curl -s -X POST "$BASE/user/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"notexist-$(date +%s)@qq.com\",\"password\":\"$PASS\"}" | python3 -m json.tool
```

**预期**：`code=404`，`message="该邮箱未注册"`

---

### 3.3 ❌ 密码错误

```bash
curl -s -X POST "$BASE/user/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EXISTING_EMAIL\",\"password\":\"$WRONG_PASS\"}" | python3 -m json.tool
```

**预期**：`code=401`，`message="密码错误"`

---

### 3.4 ❌ 缺少密码（@Valid）

```bash
curl -s -X POST "$BASE/user/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EXISTING_EMAIL\"}" | python3 -m json.tool
```

**预期**：`code=400`，`message="请输入密码"`

---

### 3.5 ❌ 连续密码错误 10 次锁定 24 小时

> ⚠️ 会锁定测试账号，仅建议在专用测试账号上执行。

```bash
for i in $(seq 1 10); do
  curl -s -X POST "$BASE/user/auth/login" -H 'Content-Type: application/json' \
    -d "{\"email\":\"$EXISTING_EMAIL\",\"password\":\"wrong99\"}" | \
    python3 -c "import sys,json; d=json.load(sys.stdin); print(i, d['code'], d['message'])"
done
```

**预期**：第 10 次后：`code=403`，`message` 含 `密码错误次数过多，账号已锁定至`

---

### 3.6 ❌ 账号已注销 / 已封禁

需先在数据库准备状态（`t_user_account.status`：`2=封禁`，`3=注销`），再登录：

**预期**：

- 注销：`code=403`，`message="账号已注销"`
- 封禁：`code=403`，`message` 含 `账号被封禁` 或 `永久封禁`

---

## 4. POST `/user/auth/reset-password` — 找回密码

### 4.1 ✅ 重置成功

```bash
clear_email_limits "$EXISTING_EMAIL"

curl -s -X POST "$BASE/user/auth/send-code" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EXISTING_EMAIL\",\"bizType\":\"RESET_PASSWORD\"}" >/dev/null

CODE="$(get_code RESET_PASSWORD "$EXISTING_EMAIL")"
NEW_PASS="newpass1"

curl -s -X POST "$BASE/user/auth/reset-password" \
  -H 'Content-Type: application/json' \
  -b "$JAR" -c "$JAR" \
  -d "{\"email\":\"$EXISTING_EMAIL\",\"password\":\"$NEW_PASS\",\"code\":\"$CODE\"}" | python3 -m json.tool
```

**预期**：`code=200`，`message="success"`，`data=null`

- 旧 refresh Cookie 被清除
- 所有会话失效，需重新登录

```bash
# 用新密码登录验证
curl -s -X POST "$BASE/user/auth/login" \
  -H 'Content-Type: application/json' -c "$JAR" \
  -d "{\"email\":\"$EXISTING_EMAIL\",\"password\":\"$NEW_PASS\"}" | python3 -m json.tool
```

---

### 4.2 ❌ 邮箱未注册

```bash
curl -s -X POST "$BASE/user/auth/reset-password" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"nobody-$(date +%s)@qq.com\",\"password\":\"$PASS\",\"code\":\"123456\"}" | python3 -m json.tool
```

**预期**：`code=400`（验证码过期）或先发码后仍可能 `404`（发码阶段就拦截）

---

### 4.3 ❌ 验证码错误

**预期**：`code=400`，`message="验证码错误"`

---

## 5. POST `/user/auth/refresh` — 刷新 accessToken

### 5.1 ✅ 刷新成功（须先登录拿到 Cookie）

```bash
# 先登录
curl -s -X POST "$BASE/user/auth/login" \
  -H 'Content-Type: application/json' \
  -c "$JAR" \
  -d "{\"email\":\"$EXISTING_EMAIL\",\"password\":\"$PASS\"}" >/dev/null

# 刷新
curl -s -X POST "$BASE/user/auth/refresh" \
  -b "$JAR" -c "$JAR" | python3 -m json.tool
```

**预期**：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "accessToken": "eyJ...",
    "accessExpiresIn": 1800
  }
}
```

- 响应会轮换新的 `game_community_refresh_token` Cookie

---

### 5.2 ❌ 无 Cookie / Cookie 为空

```bash
curl -s -X POST "$BASE/user/auth/refresh" | python3 -m json.tool
```

**预期**：`code=40102`，`message="refreshToken Cookie 不存在或已失效"`

---

### 5.3 ❌ 伪造 refreshToken

```bash
curl -s -X POST "$BASE/user/auth/refresh" \
  -H 'Cookie: game_community_refresh_token=invalid.token.here' | python3 -m json.tool
```

**预期**：`code=40102`，`message="refreshToken 无效"`

---

### 5.4 ❌ 登出后再刷新

```bash
curl -s -X POST "$BASE/user/auth/login" -H 'Content-Type: application/json' \
  -c "$JAR" -d "{\"email\":\"$EXISTING_EMAIL\",\"password\":\"$PASS\"}" >/dev/null
curl -s -X POST "$BASE/user/auth/logout" -b "$JAR" -c "$JAR" >/dev/null
curl -s -X POST "$BASE/user/auth/refresh" -b "$JAR" | python3 -m json.tool
```

**预期**：`code=40102`，`message` 含 `refreshToken 已失效` 或 `已过期`

---

## 6. POST `/user/auth/logout` — 登出

### 6.1 ✅ 已登录登出

```bash
curl -s -X POST "$BASE/user/auth/login" \
  -H 'Content-Type: application/json' \
  -c "$JAR" \
  -d "{\"email\":\"$EXISTING_EMAIL\",\"password\":\"$PASS\"}" >/dev/null

curl -s -X POST "$BASE/user/auth/logout" \
  -b "$JAR" -c "$JAR" | python3 -m json.tool
```

**预期**：`code=200`，`message="success"`，`data=null`

- refresh Cookie 被清除（`Max-Age=0`）

---

### 6.2 ✅ 未登录登出（幂等）

```bash
rm -f "$JAR"
curl -s -X POST "$BASE/user/auth/logout" | python3 -m json.tool
```

**预期**：仍返回 `code=200`（无会话可作废，仅清 Cookie）

---

### 6.3 ✅ access 过期但 Cookie 有效时登出

网关 `/user/auth/logout` 在白名单，**不需要** `Authorization` 头；服务端从 refresh Cookie 解析会话并作废。

```bash
curl -s -X POST "$BASE/user/auth/logout" -b "$JAR" -c "$JAR" | python3 -m json.tool
```

**预期**：`code=200`

---

## 7. 网关鉴权（补充）

认证接口均在网关白名单，**无需** `Authorization`。

访问需登录接口（如 `/api/user/me`）时：

```bash
# 无 Token
curl -s http://localhost:8080/api/user/me | python3 -m json.tool
# 预期：HTTP 401，code=40101，message="请先登录"

# 有 Token
curl -s http://localhost:8080/api/user/me \
  -H "Authorization: Bearer $TOKEN" | python3 -m json.tool
# 预期：code=200
```

---

## 8. Mock 模式（开发）

`.env` 开启：

```env
EMAIL_MOCK_ENABLED=true
EMAIL_MOCK_ADDRESSES=*
EMAIL_MOCK_FIXED_CODE=123456
```

此时发码不会真实发邮件，Redis 中验证码固定为 `123456`，日志含 `[邮箱模拟发送]`。

---

## 9. 一键冒烟脚本

```bash
./scripts/test-auth-api.sh
```

自动跑：发码 → 注册 → 登录 → 刷新 → 登出（使用随机邮箱）。

---

## 10. IDE 插件一键发送（推荐）

使用 **`.http` 格式**，IntelliJ / VS Code REST Client 可直接点 ▶ 发送：

| 文件 | 说明 |
|------|------|
| [`scripts/http/auth-api.http`](http/auth-api.http) | 全部用例，点请求旁绿色箭头运行 |
| [`scripts/http/http-client.env.json`](http/http-client.env.json) | 环境变量（`dev` / `direct`） |

**IntelliJ IDEA**：打开 `auth-api.http` → 右上角选 `dev` 环境 → 点击请求左侧 ▶

**VS Code / Cursor**：安装 [REST Client](https://marketplace.visualstudio.com/items?itemName=humao.rest-client) 插件 → 打开 `auth-api.http` → `Send Request`

私有变量可复制 `http-client.private.env.json.example` 为 `http-client.private.env.json` 覆盖邮箱/密码。

---

## 11. Apifox 导入

| 文件 | 说明 |
|------|------|
| [`scripts/apifox/auth-api.postman_collection.json`](apifox/auth-api.postman_collection.json) | 接口集合（Postman 格式，Apifox 直接导入） |
| [`scripts/apifox/auth-api.postman_environment.json`](apifox/auth-api.postman_environment.json) | 本地环境变量 |
| [`scripts/apifox/README.md`](apifox/README.md) | 导入步骤 |

**Apifox**：导入 → Postman → 选 collection 文件 → 再导入 environment 文件 → 切换环境「游戏社区-本地开发」。

---

## Redis Key 速查

| Key | 说明 |
|-----|------|
| `email:code:REGISTER:<email>` | 注册验证码 |
| `email:code:RESET_PASSWORD:<email>` | 找回密码验证码 |
| `email:cooldown:<biz>:<email>` | 60s 发码冷却 |
| `email:daily:<biz>:<email>` | 当日发码次数 |
| `email:verify:fail:<email>` | 验证码错误计数 |
| `email:verify:lock:<email>` | 验证码验证锁定 |
| `user:session:<sessionId>` | 会话 |
| `user:active-session:<userId>` | 用户当前活跃会话 |
