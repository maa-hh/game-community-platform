#!/bin/bash
# 用户资料审核真实联调：注册登录、提交异步审核、校验状态回写和版本冲突。
#
# 该脚本经网关调用真实 user-service；审核文本使用词库中的高置信敏感词，
# 预期由 ai-agent-service 本地 AC 自动机直接拒绝，不依赖外部大模型返回。

set -euo pipefail

API="${API_BASE:-http://127.0.0.1:8080}"
REDIS_CONTAINER="${REDIS_CONTAINER:-redis}"
# 注册默认昵称取邮箱本地部分，保持在 t_user.username 的 20 字符限制内。
EMAIL="${TEST_AUDIT_EMAIL:-aud$(date +%s)@qq.com}"
PASSWORD="${TEST_AUDIT_PASSWORD:-TestPass1}"
COOKIE_JAR="$(mktemp)"
TMP="$(mktemp)"
trap 'rm -f "$COOKIE_JAR" "$TMP"' EXIT

pass() { echo "✓ $1"; }
fail() { echo "✗ $1" >&2; exit 1; }

json_code() {
  python3 -c "import json,sys; print(json.load(sys.stdin).get('code','?'))"
}

json_data_field() {
  python3 -c "import json,sys; d=json.load(sys.stdin).get('data') or {}; print(d.get(sys.argv[1],''))" "$1"
}

redis_get() {
  docker exec "$REDIS_CONTAINER" redis-cli GET "$1" 2>/dev/null | tr -d '\r"'
}

request_json() {
  local method="$1" path="$2" body="${3:-}"
  if [[ -n "$body" ]]; then
    curl -sS -X "$method" "$API$path" \
      -H 'Content-Type: application/json' \
      -b "$COOKIE_JAR" -c "$COOKIE_JAR" \
      -d "$body"
  else
    curl -sS -X "$method" "$API$path" \
      -b "$COOKIE_JAR" -c "$COOKIE_JAR"
  fi
}

echo "=== 用户资料审核真实联调 ==="
echo "API=$API"

# 先验证网关可达，避免把环境故障误判为业务失败。
curl -sS -o /dev/null -w "%{http_code}" "$API/user/me" \
  | grep -qE '401|403|200' || fail "网关不可达"
pass "网关连通"

# 注册前清理本次邮箱可能残留的验证码限制。
LOWER_EMAIL="$(echo "$EMAIL" | tr '[:upper:]' '[:lower:]')"
docker exec "$REDIS_CONTAINER" redis-cli DEL \
  "email:cooldown:REGISTER:$LOWER_EMAIL" \
  "email:daily:REGISTER:$LOWER_EMAIL" \
  "email:verify:fail:$LOWER_EMAIL" \
  "email:verify:lock:$LOWER_EMAIL" \
  "email:code:REGISTER:$LOWER_EMAIL" >/dev/null 2>&1 || true

# 真实注册并登录，获取网关认可的 Access Token 和 Refresh Cookie。
response="$(request_json POST /user/auth/send-code "{\"email\":\"$EMAIL\",\"bizType\":\"REGISTER\"}")"
[[ "$(echo "$response" | json_code)" == "200" ]] || fail "send-code: $response"
code="$(redis_get "email:code:REGISTER:$LOWER_EMAIL")"
[[ -n "$code" && "$code" != "(nil)" ]] || fail "Redis 中没有验证码"
pass "真实发送验证码并从 Redis 读取验证码"

response="$(request_json POST /user/auth/register "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\",\"code\":\"$code\"}")"
[[ "$(echo "$response" | json_code)" == "200" ]] || fail "register: $response"
response="$(request_json POST /user/auth/login "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}")"
[[ "$(echo "$response" | json_code)" == "200" ]] || fail "login: $response"
TOKEN="$(echo "$response" | json_data_field accessToken)"
[[ -n "$TOKEN" ]] || fail "login 没有 accessToken"
AUTH_HEADER="Authorization: Bearer $TOKEN"
pass "真实登录"

# 查询当前版本；资料审核回写使用该版本做乐观锁 CAS。
response="$(curl -sS -X GET "$API/user/me" -H "$AUTH_HEADER" -b "$COOKIE_JAR" -c "$COOKIE_JAR")"
[[ "$(echo "$response" | json_code)" == "200" ]] || fail "get /user/me: $response"
VERSION="$(echo "$response" | json_data_field version)"
[[ "$VERSION" =~ ^[0-9]+$ ]] || fail "资料版本异常: $VERSION"
pass "读取资料版本 version=$VERSION"

# 提交确定性敏感词，验证接口快速返回任务，而不是同步等待 AI。
response="$(curl -sS -X PUT "$API/user/signature" \
  -H "$AUTH_HEADER" -H 'Content-Type: application/json' \
  -b "$COOKIE_JAR" -c "$COOKIE_JAR" \
  -d "{\"version\":$VERSION,\"signature\":\"网络赌博\"}")"
[[ "$(echo "$response" | json_code)" == "200" ]] || fail "submit signature: $response"
TASK_ID="$(echo "$response" | json_data_field taskId)"
AUDIT_STATUS="$(echo "$response" | json_data_field auditStatus)"
[[ "$TASK_ID" =~ ^[0-9]+$ ]] || fail "没有返回 taskId: $response"
[[ "$AUDIT_STATUS" == "1" ]] || fail "提交后审核状态不是 AUDITING: $response"
pass "异步提交成功 taskId=${TASK_ID}，立即返回 AUDITING"

# 轮询 /user/me，确认后台任务最终释放字段占用并清空 pending 值。
for attempt in {1..20}; do
  response="$(curl -sS -X GET "$API/user/me" -H "$AUTH_HEADER" -b "$COOKIE_JAR" -c "$COOKIE_JAR")"
  current_status="$(echo "$response" | json_data_field signatureAuditStatus)"
  pending="$(echo "$response" | json_data_field pendingSignature)"
  if [[ "$current_status" == "0" && -z "$pending" ]]; then
    pass "异步审核完成：字段状态恢复 NONE，pending 已清理"
    break
  fi
  if [[ "$attempt" == "20" ]]; then
    fail "异步审核未在预期时间完成: $response"
  fi
  sleep 1
done

# 使用必然过期的版本提交，验证乐观锁冲突不会创建新的审核结果。
response="$(curl -sS -X PUT "$API/user/signature" \
  -H "$AUTH_HEADER" -H 'Content-Type: application/json' \
  -b "$COOKIE_JAR" -c "$COOKIE_JAR" \
  -d '{"version":-1,"signature":"stale-version-test"}')"
[[ "$(echo "$response" | json_code)" == "409" ]] || fail "旧版本请求未返回 409: $response"
pass "旧资料版本被 CAS 拒绝（409）"

echo "=== 用户资料审核真实联调通过 ==="
