#!/usr/bin/env bash
# P1 真实联通测试：认证 → 游客读帖 → 赞/藏/评/回 → 收藏列表 → 分享
# 依赖：网关 8080、MySQL/Redis/Nacos、user/content/social 已启动
#
# 用法：
#   ./scripts/test-post-social-api.sh
#   API_BASE=http://127.0.0.1:8080 TEST_EMAIL=xxx@qq.com TEST_PASSWORD=abc123 ./scripts/test-post-social-api.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "$SCRIPT_DIR/lib/common.sh"
load_env

API="${API_BASE:-http://127.0.0.1:8080}"
EMAIL="${TEST_EMAIL:-p1-test-$(date +%s)@qq.com}"
PASS="${TEST_PASSWORD:-abc12345}"
REDIS_CONTAINER="${REDIS_CONTAINER:-redis}"
COOKIE_JAR="$(mktemp)"
TMP="$(mktemp)"
AUTH_HEADER=""
ARTICLE_ID=""
COMMENT_ID=""
REPLY_ID=""
CATEGORY_ID=""
trap 'rm -f "$COOKIE_JAR" "$TMP"' EXIT

pass() { echo "✓ $1"; }
fail() { echo "✗ $1"; echo "---- response ----"; cat "$TMP" 2>/dev/null || true; exit 1; }
warn() { echo "⚠ $1"; }
section() { echo ""; echo "=== $1 ==="; }

json_get() {
  local expr="$1"
  python3 -c "import sys,json; d=json.load(sys.stdin); print($expr)" 2>/dev/null
}

curl_json() {
  local method="$1" path="$2" body="${3:-}"
  local url="$API$path"
  local -a args=(-sS -X "$method" "$url" -b "$COOKIE_JAR" -c "$COOKIE_JAR")
  if [[ -n "$AUTH_HEADER" ]]; then
    args+=(-H "$AUTH_HEADER")
  fi
  if [[ -n "$body" ]]; then
    args+=(-H 'Content-Type: application/json' -d "$body")
  fi
  curl "${args[@]}" >"$TMP" || fail "curl failed: $method $path"
  cat "$TMP"
}

expect_code() {
  local want="$1" label="$2"
  local got
  got="$(json_get 'd.get("code","?")' <"$TMP")"
  [[ "$got" == "$want" ]] || fail "$label (code=$got, want=$want)"
  pass "$label"
}

redis_get_code() {
  local key="$1"
  docker exec "$REDIS_CONTAINER" redis-cli GET "$key" 2>/dev/null | tr -d '\r"'
}

clear_email_limits() {
  local lower
  lower="$(echo "$EMAIL" | tr '[:upper:]' '[:lower:]')"
  docker exec "$REDIS_CONTAINER" redis-cli DEL \
    "email:cooldown:REGISTER:${lower}" \
    "email:daily:REGISTER:${lower}" \
    "email:verify:fail:${lower}" \
    "email:verify:lock:${lower}" \
    "email:code:REGISTER:${lower}" \
    "email:cooldown:LOGIN:${lower}" \
    "email:code:LOGIN:${lower}" >/dev/null 2>&1 || true
}

sql_exec() {
  docker exec -i "${MYSQL_CONTAINER:-mysql}" mysql --default-character-set=utf8mb4 -u"${MYSQL_USER:-root}" -p"${MYSQL_PASSWORD:-root123}" -N -e "$1" 2>/dev/null
}

echo "############################################"
echo "# P1 Post/Social/Favorite 真实联通测试"
echo "# API=$API"
echo "# EMAIL=$EMAIL"
echo "############################################"

section "0. 网关连通"
HTTP_CODE="$(curl -s -o /dev/null -w '%{http_code}' "$API/article/latest?size=1" || true)"
[[ "$HTTP_CODE" =~ ^(200|401)$ ]] || fail "网关不可达 HTTP=$HTTP_CODE ($API)"
pass "网关可达 (HTTP $HTTP_CODE)"

section "1. 游客可读：最新帖 / 详情白名单"
curl_json GET '/article/latest?size=5' >/dev/null
expect_code 200 "游客 GET /article/latest"
LATEST_COUNT="$(json_get 'len(d.get("data") or [])' <"$TMP")"
pass "最新帖数量=$LATEST_COUNT"

# 若已有已发布帖，记下 id 供后续只读校验
EXISTING_ID="$(json_get '(d.get("data") or [{}])[0].get("id","") if (d.get("data") or []) else ""' <"$TMP")"

section "2. 注册 / 登录"
clear_email_limits
curl_json POST /user/auth/send-code "{\"email\":\"$EMAIL\",\"bizType\":\"REGISTER\"}" >/dev/null
CODE_SEND="$(json_get 'd.get("code")' <"$TMP")"
if [[ "$CODE_SEND" != "200" ]]; then
  # 可能邮箱已存在，直接登录
  warn "发码失败(code=$CODE_SEND)，尝试直接登录已有账号"
else
  pass "发送注册验证码"
  CODE="$(redis_get_code "email:code:REGISTER:$(echo "$EMAIL" | tr '[:upper:]' '[:lower:]')")"
  if [[ -z "$CODE" || "$CODE" == "(nil)" ]]; then
    CODE="${EMAIL_MOCK_FIXED_CODE:-123456}"
    warn "Redis 无验证码，改用 EMAIL_MOCK_FIXED_CODE=$CODE"
  else
    pass "Redis 验证码=$CODE"
  fi
  curl_json POST /user/auth/register "{\"email\":\"$EMAIL\",\"password\":\"$PASS\",\"code\":\"$CODE\"}" >/dev/null
  REG_CODE="$(json_get 'd.get("code")' <"$TMP")"
  if [[ "$REG_CODE" == "200" ]]; then
    pass "注册成功"
  else
    warn "注册返回 code=$REG_CODE，继续尝试登录"
  fi
fi

curl_json POST /user/auth/login "{\"email\":\"$EMAIL\",\"password\":\"$PASS\"}" >/dev/null
expect_code 200 "登录"
TOKEN="$(json_get 'd.get("data",{}).get("accessToken","")' <"$TMP")"
ACCOUNT_ID="$(json_get 'd.get("data",{}).get("user",{}).get("accountId","")' <"$TMP")"
[[ -n "$TOKEN" ]] || fail "未拿到 accessToken"
AUTH_HEADER="Authorization: Bearer $TOKEN"
pass "拿到 token，accountId=$ACCOUNT_ID"

section "3. 分区 & 发帖（进待审）后强制发布"
curl_json GET '/category/list?page=1&size=20' >/dev/null
expect_code 200 "分区列表"
CATEGORY_ID="$(json_get 'next((x.get("id") for x in (d.get("data") or []) if x.get("id")), "")' <"$TMP")"
if [[ -z "$CATEGORY_ID" ]]; then
  # 兜底插入一个分区
  sql_exec "USE game_community; INSERT INTO t_category(name,description,status,sort,create_time,update_time,deleted) VALUES('联调分区','p1',1,1,NOW(),NOW(),0);" || true
  CATEGORY_ID="$(sql_exec "USE game_community; SELECT id FROM t_category WHERE deleted=0 ORDER BY id LIMIT 1;")"
fi
[[ -n "$CATEGORY_ID" ]] || fail "无可用 categoryId"
pass "categoryId=$CATEGORY_ID"

TITLE="P1联调-$(date +%H%M%S)"
curl_json POST /article "{\"title\":\"$TITLE\",\"summary\":\"联调摘要\",\"content\":\"这是联调正文内容，用于评论点赞收藏。\",\"categoryId\":$CATEGORY_ID,\"postType\":1,\"status\":2,\"imageUrls\":[\"https://picsum.photos/seed/p1/640/360\"]}" >/dev/null
expect_code 200 "创建图文帖（待审）"
ARTICLE_ID="$(json_get 'd.get("data")' <"$TMP")"
[[ -n "$ARTICLE_ID" && "$ARTICLE_ID" != "None" ]] || fail "未返回 articleId"
pass "articleId=$ARTICLE_ID"

# 审核链路可能未完全跑通：直接 SQL 置为已发布，保证后续社交接口可测
sql_exec "USE game_community; UPDATE t_article SET status=1, published_time=NOW(), update_time=NOW() WHERE id=$ARTICLE_ID;" \
  || fail "SQL 发布失败（检查 MySQL 容器与库名）"
pass "SQL 强制发布 article=$ARTICLE_ID"

section "4. 游客/登录读详情 + 统计"
# 清掉 Authorization 测游客（另开一次无 header）
GUEST_TMP="$(mktemp)"
curl -sS "$API/article/$ARTICLE_ID" >"$GUEST_TMP" || fail "游客详情请求失败"
python3 -c "import json;d=json.load(open('$GUEST_TMP'));assert d.get('code')==200,d;assert d['data']['id']==$ARTICLE_ID" \
  || fail "游客读详情失败"
pass "游客可读详情"
rm -f "$GUEST_TMP"

curl_json GET "/article/$ARTICLE_ID" >/dev/null
expect_code 200 "登录读详情"
DETAIL_USER="$(json_get 'd.get("data",{}).get("username") or ""' <"$TMP")"
pass "详情作者字段 username=${DETAIL_USER:-空}"

curl_json GET "/social/article/count/$ARTICLE_ID" >/dev/null
expect_code 200 "文章统计"
pass "统计 liked=$(json_get 'd.get("data",{}).get("liked")' <"$TMP") favorited=$(json_get 'd.get("data",{}).get("favorited")' <"$TMP")"

section "5. 点赞 / 收藏"
curl_json POST "/social/like/article/$ARTICLE_ID" >/dev/null
expect_code 200 "点赞"
curl_json GET "/social/article/count/$ARTICLE_ID" >/dev/null
LIKED="$(json_get 'd.get("data",{}).get("liked")' <"$TMP")"
LIKE_COUNT="$(json_get 'd.get("data",{}).get("likeCount")' <"$TMP")"
[[ "$LIKED" == "True" || "$LIKED" == "true" ]] || fail "点赞后 liked=$LIKED"
pass "点赞态 liked=$LIKED count=$LIKE_COUNT"

curl_json POST "/social/favorite/article/$ARTICLE_ID" >/dev/null
expect_code 200 "收藏"
curl_json GET "/social/favorite/article/check/$ARTICLE_ID" >/dev/null
FAV="$(json_get 'd.get("data")' <"$TMP")"
[[ "$FAV" == "True" || "$FAV" == "true" ]] || fail "收藏检查失败 data=$FAV"
pass "收藏检查通过"

curl_json GET '/social/favorite/article/list?page=1&size=20' >/dev/null
expect_code 200 "我的收藏列表"
FAV_HIT="$(json_get "any(str(x.get('id'))=='$ARTICLE_ID' for x in (d.get('data') or []))" <"$TMP")"
[[ "$FAV_HIT" == "True" ]] || fail "收藏列表未包含 article=$ARTICLE_ID"
pass "收藏列表包含当前帖"

section "6. 评论 / 回复 / 点赞评论"
curl_json POST /social/comment "{\"articleId\":$ARTICLE_ID,\"content\":\"联调一级评论\"}" >/dev/null
expect_code 200 "发评论"
COMMENT_ID="$(json_get 'd.get("data")' <"$TMP")"
pass "commentId=$COMMENT_ID"

curl_json GET "/social/comment/list/$ARTICLE_ID?page=1&size=20" >/dev/null
expect_code 200 "评论列表"
curl_json POST /social/reply "{\"commentId\":$COMMENT_ID,\"content\":\"联调二级回复\",\"replyToAccountId\":$ACCOUNT_ID}" >/dev/null
expect_code 200 "发回复"
REPLY_ID="$(json_get 'd.get("data")' <"$TMP")"
pass "replyId=$REPLY_ID"

curl_json GET "/social/reply/list/$COMMENT_ID?page=1&size=20" >/dev/null
expect_code 200 "回复列表"
curl_json POST "/social/like/comment/$COMMENT_ID" >/dev/null
expect_code 200 "赞评论"
curl_json POST "/social/like/reply/$REPLY_ID" >/dev/null
expect_code 200 "赞回复"

section "7. 分享计数 + 取消赞藏"
curl_json POST "/social/share/article/$ARTICLE_ID" '{"channel":"link"}' >/dev/null
expect_code 200 "分享计数"
curl_json GET "/social/article/count/$ARTICLE_ID" >/dev/null
SHARE_COUNT="$(json_get 'd.get("data",{}).get("shareCount")' <"$TMP")"
pass "shareCount=$SHARE_COUNT"

curl_json DELETE "/social/like/article/$ARTICLE_ID" >/dev/null
expect_code 200 "取消点赞"
curl_json DELETE "/social/favorite/article/$ARTICLE_ID" >/dev/null
expect_code 200 "取消收藏"
curl_json POST "/social/favorite/article/$ARTICLE_ID" >/dev/null
expect_code 200 "再次收藏（个人页展示用）"

section "8. 批量统计 + 用户名片"
curl_json GET "/social/article/counts?articleIds=$ARTICLE_ID" >/dev/null
expect_code 200 "批量统计"
curl_json GET "/user/ids?ids=$ACCOUNT_ID" >/dev/null
expect_code 200 "批量用户名片"

section "9. 举报（可选）"
curl_json POST /report "{\"targetType\":1,\"targetId\":$ARTICLE_ID,\"reason\":\"联调举报\"}" >/dev/null
RCODE="$(json_get 'd.get("code")' <"$TMP")"
if [[ "$RCODE" == "200" ]]; then
  pass "举报提交成功"
else
  warn "举报返回 code=$RCODE（可忽略，若重复举报）"
fi

echo ""
echo "############################################"
echo "# 全部关键路径通过"
echo "# articleId=$ARTICLE_ID commentId=$COMMENT_ID replyId=$REPLY_ID"
echo "# 前端可打开: http://localhost:3000/post/$ARTICLE_ID"
echo "# 个人页收藏应可见该帖"
echo "############################################"
