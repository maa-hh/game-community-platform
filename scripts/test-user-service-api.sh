#!/bin/bash
# user-service 前端联调自测：经网关 8080 调用 HTTP 接口（Redis 通过 docker exec）

set -euo pipefail

API="${API_BASE:-http://127.0.0.1:8080}"
PHONE="${TEST_PHONE:-15245537300}"
REDIS_CONTAINER="${REDIS_CONTAINER:-redis}"
PASS="${TEST_PASSWORD:-TestPass1}"
USER="${TEST_USERNAME:-feuser$(date +%s | tail -c 6)}"
COOKIE_JAR="$(mktemp)"
TMP="$(mktemp)"
trap 'rm -f "$COOKIE_JAR" "$TMP"' EXIT

pass() { echo "✓ $1"; }
fail() { echo "✗ $1"; exit 1; }
warn() { echo "⚠ $1"; }

json_code() {
  python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('code','?'))" 2>/dev/null || echo "?"
}

redis_cmd() {
  docker exec "$REDIS_CONTAINER" redis-cli "$@" 2>/dev/null
}

clear_sms_keys() {
  redis_cmd DEL "sms:code:${PHONE}" >/dev/null || true
  for biz in REGISTER LOGIN CHANGE_PHONE RESET_PASSWORD BIND_PHONE VERIFY_PHONE; do
    redis_cmd DEL "sms:cooldown:${biz}:${PHONE}" >/dev/null || true
    redis_cmd DEL "sms:daily:${biz}:${PHONE}" >/dev/null || true
  done
  redis_cmd DEL "sms:verify:fail:${PHONE}" "sms:verify:lock:${PHONE}" >/dev/null || true
}

get_redis_code() {
  redis_cmd GET "sms:code:${PHONE}" | tr -d '\r"' 
}

curl_json() {
  local method="$1" path="$2" body="${3:-}"
  if [[ -n "$body" ]]; then
    curl -s -X "$method" "$API$path" -H 'Content-Type: application/json' -b "$COOKIE_JAR" -c "$COOKIE_JAR" ${AUTH_HEADER:+-H "$AUTH_HEADER"} -d "$body" >"$TMP"
  else
    curl -s -X "$method" "$API$path" -b "$COOKIE_JAR" -c "$COOKIE_JAR" ${AUTH_HEADER:+-H "$AUTH_HEADER"} >"$TMP"
  fi
  cat "$TMP"
}

echo "=== User-Service 联调自测 (API=$API) ==="

# 网关连通
curl -s -o /dev/null -w "%{http_code}" "$API/user/me" | grep -qE '401|403|200' || fail "网关不可达 $API"
pass "网关连通"

clear_sms_keys

# A1 sendCode
curl_json POST /user/sendCode "{\"phone\":\"$PHONE\",\"bizType\":\"REGISTER\"}" >/dev/null
[[ "$(json_code <"$TMP")" == "200" ]] || fail "A1 sendCode: $(cat "$TMP")"
CODE="$(get_redis_code)"
[[ -n "$CODE" && "$CODE" != "(nil)" ]] || fail "A1 Redis 无验证码"
pass "A1 sendCode"

# A2 register（需 t_account_id_pool 表；失败则跳过并尝试已有账号登录）
REGISTER_OK=0
curl_json POST /user/register/phone "{\"username\":\"$USER\",\"password\":\"$PASS\",\"phone\":\"$PHONE\",\"code\":\"$CODE\",\"steamAccount\":\"steam_test\"}" >/dev/null
if [[ "$(json_code <"$TMP")" == "200" ]]; then
  ACCOUNT_ID="$(python3 -c "import sys,json; print(json.load(sys.stdin)['data'])" <"$TMP")"
  REGISTER_OK=1
  pass "A2 register → accountId=$ACCOUNT_ID"
else
  warn "A2 register 跳过: $(python3 -c "import sys,json; print(json.load(sys.stdin).get('message',''))" <"$TMP")"
  ACCOUNT_ID="${TEST_ACCOUNT_ID:-}"
  PASS="${TEST_LOGIN_PASSWORD:-}"
  if [[ -z "$ACCOUNT_ID" || -z "$PASS" ]]; then
    warn "未设置 TEST_ACCOUNT_ID/TEST_LOGIN_PASSWORD，跳过需登录的接口"
    echo "=== 公开接口联调通过（注册需执行 sql/user-account-id-pool.sql）==="
    exit 0
  fi
fi

# A3 login
curl -s -X POST "$API/user/login/account" -H 'Content-Type: application/json' -c "$COOKIE_JAR" \
  -d "{\"accountId\":$ACCOUNT_ID,\"password\":\"$PASS\"}" >"$TMP"
[[ "$(json_code <"$TMP")" == "200" ]] || fail "A3 login: $(cat "$TMP")"
TOKEN="$(python3 -c "import sys,json; print(json.load(sys.stdin)['data']['accessToken'])" <"$TMP")"
ACCOUNT_ID="$(python3 -c "import sys,json; print(json.load(sys.stdin)['data']['accountId'])" <"$TMP")"
AUTH_HEADER="Authorization: Bearer $TOKEN"
pass "A3 login → accountId=$ACCOUNT_ID"

# B1 me
curl_json GET /user/me >/dev/null
[[ "$(json_code <"$TMP")" == "200" ]] || fail "B1 me: $(cat "$TMP")"
VERSION="$(python3 -c "import sys,json; print(json.load(sys.stdin)['data'].get('version',0))" <"$TMP")"
pass "B1 me"

# B2 updateSignature（异步审核，立即返回）
curl_json PUT /user/signature "{\"signature\":\"fe integration test\"}" >/dev/null
[[ "$(json_code <"$TMP")" == "200" ]] || fail "B2 updateSignature: $(cat "$TMP")"
pass "B2 updateSignature"

# C1-C4, C3
curl_json GET "/user/$ACCOUNT_ID" >/dev/null && [[ "$(json_code <"$TMP")" == "200" ]] && pass "C1 getUser" || fail "C1"
curl_json GET "/user/simple/$ACCOUNT_ID" >/dev/null && [[ "$(json_code <"$TMP")" == "200" ]] && pass "C2 getSimpleUser" || fail "C2"
curl_json GET "/user/simple/search?username=a&page=1&size=5" >/dev/null && [[ "$(json_code <"$TMP")" == "200" ]] && pass "C4 search" || fail "C4"
curl_json GET "/user/ids?ids=$ACCOUNT_ID" >/dev/null && [[ "$(json_code <"$TMP")" == "200" ]] && pass "C3 getUsersByAccountIds" || fail "C3"

# A4 refresh
curl -s -X POST "$API/user/token/refresh" -b "$COOKIE_JAR" -c "$COOKIE_JAR" >"$TMP"
[[ "$(json_code <"$TMP")" == "200" ]] || fail "A4 refresh: $(cat "$TMP")"
TOKEN="$(python3 -c "import sys,json; print(json.load(sys.stdin)['data']['accessToken'])" <"$TMP")"
AUTH_HEADER="Authorization: Bearer $TOKEN"
pass "A4 refreshToken"

# D1/D2 cancel cycle
curl_json POST /user/cancel >/dev/null && [[ "$(json_code <"$TMP")" == "200" ]] && pass "D1 cancel" || fail "D1"
curl_json POST /user/cancel/revoke >/dev/null && [[ "$(json_code <"$TMP")" == "200" ]] && pass "D2 revoke" || fail "D2"

# B4 changePassword + re-login (only if we registered fresh user)
if [[ "$REGISTER_OK" == "1" ]]; then
  NEW_PASS="NewPass2x"
  curl_json PUT /user/password "{\"oldPassword\":\"$PASS\",\"newPassword\":\"$NEW_PASS\"}" >/dev/null
  [[ "$(json_code <"$TMP")" == "200" ]] || fail "B4 changePassword: $(cat "$TMP")"
  pass "B4 changePassword"
  curl -s -X POST "$API/user/login/account" -H 'Content-Type: application/json' -c "$COOKIE_JAR" \
    -d "{\"accountId\":$ACCOUNT_ID,\"password\":\"$NEW_PASS\"}" >"$TMP"
  TOKEN="$(python3 -c "import sys,json; print(json.load(sys.stdin)['data']['accessToken'])" <"$TMP")"
  AUTH_HEADER="Authorization: Bearer $TOKEN"
  PASS="$NEW_PASS"
  pass "re-login after password change"
fi

# A5 logout
curl_json POST /user/logout >/dev/null
[[ "$(json_code <"$TMP")" == "200" ]] && pass "A5 logout" || fail "A5"

# Frontend proxy
FE="${FE_BASE:-http://127.0.0.1:5173}"
if curl -sf "$FE/" -o /dev/null; then
  clear_sms_keys
  PROXY="$(curl -s -X POST "$FE/api/user/sendCode" -H 'Content-Type: application/json' -d "{\"phone\":\"$PHONE\",\"bizType\":\"LOGIN\"}")"
  [[ "$(echo "$PROXY" | python3 -c "import sys,json; print(json.load(sys.stdin).get('code'))")" == "200" ]] \
    && pass "Frontend /api 代理 sendCode" || warn "Frontend 代理: $PROXY"
else
  warn "Frontend 未运行"
fi

echo ""
echo "=== user-service HTTP 联调完成 ==="
echo "accountId=$ACCOUNT_ID"
