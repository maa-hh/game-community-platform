#!/bin/bash
# 认证接口冒烟测试：发码 → 注册 → 登录 → 刷新 → 登出
# 详细用例见 scripts/test-auth-api.md

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "$SCRIPT_DIR/lib/common.sh"
load_env

API="${API_BASE:-http://127.0.0.1:8080}"
EMAIL="${TEST_EMAIL:-test-$(date +%s)@qq.com}"
PASS="${TEST_PASSWORD:-abc123}"
REDIS_CONTAINER="${REDIS_CONTAINER:-redis}"
COOKIE_JAR="$(mktemp)"
TMP="$(mktemp)"
trap 'rm -f "$COOKIE_JAR" "$TMP"' EXIT

pass() { echo "✓ $1"; }
fail() { echo "✗ $1"; exit 1; }

json_code() {
  python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('code','?'))" 2>/dev/null || echo "?"
}

json_msg() {
  python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('message',''))" 2>/dev/null || echo "?"
}

redis_get() {
  docker exec "$REDIS_CONTAINER" redis-cli GET "$1" 2>/dev/null | tr -d '\r"'
}

clear_limits() {
  local lower
  lower="$(echo "$EMAIL" | tr '[:upper:]' '[:lower:]')"
  docker exec "$REDIS_CONTAINER" redis-cli DEL \
    "email:cooldown:REGISTER:${lower}" \
    "email:daily:REGISTER:${lower}" \
    "email:verify:fail:${lower}" \
    "email:verify:lock:${lower}" \
    "email:code:REGISTER:${lower}" >/dev/null 2>&1 || true
}

curl_json() {
  local method="$1" path="$2" body="${3:-}"
  local extra_args=()
  if [[ -n "${4:-}" ]]; then
  read -r -a extra_args <<< "$4"
  fi
  if [[ -n "$body" ]]; then
    curl -s -X "$method" "$API$path" \
      -H 'Content-Type: application/json' \
      -b "$COOKIE_JAR" -c "$COOKIE_JAR" \
      "${extra_args[@]}" \
      -d "$body" >"$TMP"
  else
    curl -s -X "$method" "$API$path" \
      -b "$COOKIE_JAR" -c "$COOKIE_JAR" \
      "${extra_args[@]}" >"$TMP"
  fi
  cat "$TMP"
}

echo "=== 认证接口冒烟测试 ==="
echo "API=$API"
echo "EMAIL=$EMAIL"
echo ""

# 网关连通
curl -s -o /dev/null -w "%{http_code}" "$API/user/auth/send-code" \
  -H 'Content-Type: application/json' -d '{"email":"x"}' | grep -qE '200|400' \
  || fail "网关不可达: $API"
pass "网关连通"

clear_limits

# send-code
curl_json POST /user/auth/send-code "{\"email\":\"$EMAIL\",\"bizType\":\"REGISTER\"}" >/dev/null
[[ "$(json_code <"$TMP")" == "200" ]] || fail "send-code: $(cat "$TMP")"
pass "send-code"

CODE="$(redis_get "email:code:REGISTER:$(echo "$EMAIL" | tr '[:upper:]' '[:lower:]')")"
if [[ -z "$CODE" || "$CODE" == "(nil)" ]]; then
  if [[ "${EMAIL_MOCK_ENABLED:-false}" == "true" ]]; then
    CODE="${EMAIL_MOCK_FIXED_CODE:-123456}"
    pass "获取验证码 (mock 固定码 $CODE)"
  else
    fail "Redis 无验证码，请检查 SMTP 或开启 EMAIL_MOCK_ENABLED"
  fi
else
  pass "获取验证码 ($CODE)"
fi

# register
curl_json POST /user/auth/register "{\"email\":\"$EMAIL\",\"password\":\"$PASS\",\"code\":\"$CODE\"}" >/dev/null
[[ "$(json_code <"$TMP")" == "200" ]] || fail "register: $(cat "$TMP")"
pass "register"

# login
curl_json POST /user/auth/login "{\"email\":\"$EMAIL\",\"password\":\"$PASS\"}" >/dev/null
[[ "$(json_code <"$TMP")" == "200" ]] || fail "login: $(cat "$TMP")"
TOKEN="$(python3 -c "import sys,json; print(json.load(sys.stdin)['data']['accessToken'])" <"$TMP")"
[[ -n "$TOKEN" ]] || fail "login 无 accessToken"
pass "login"

# refresh
curl_json POST /user/auth/refresh >/dev/null
[[ "$(json_code <"$TMP")" == "200" ]] || fail "refresh: $(cat "$TMP")"
pass "refresh"

# logout
curl_json POST /user/auth/logout >/dev/null
[[ "$(json_code <"$TMP")" == "200" ]] || fail "logout: $(cat "$TMP")"
pass "logout"

# refresh after logout should fail
curl_json POST /user/auth/refresh >/dev/null
[[ "$(json_code <"$TMP")" == "40102" ]] || fail "refresh after logout 应为 40102: $(cat "$TMP")"
pass "logout 后会话失效 (40102)"

echo ""
echo "=== 冒烟测试通过 ==="
echo "完整用例手册: scripts/test-auth-api.md"
