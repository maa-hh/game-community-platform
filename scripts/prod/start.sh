#!/bin/bash
# 生产环境启动：Docker + 数据库同步 + 后台启动 gateway / user-service
# 用法：
#   cp .env.example .env   # 填写生产配置
#   ./scripts/prod/start.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "$SCRIPT_DIR/../lib/common.sh"

export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-prod}"

load_env

if [[ ! -f "$ENV_FILE" ]]; then
  echo "缺少 $ENV_FILE，请先：cp .env.example .env 并填写生产配置" >&2
  exit 1
fi

if [[ "${JWT_ACCESS_SECRET:-}" == *"请生成"* ]] || [[ "${JWT_REFRESH_SECRET:-}" == *"请生成"* ]]; then
  echo "请在 .env 中设置 JWT_ACCESS_SECRET / JWT_REFRESH_SECRET（≥32 字符随机串）" >&2
  exit 1
fi

if [[ "${EMAIL_MOCK_ENABLED:-false}" == "true" ]]; then
  echo "警告: 生产环境 EMAIL_MOCK_ENABLED=true，将不会真实发信" >&2
fi

if [[ -z "${SMTP_HOST:-}" ]]; then
  echo "错误: 生产环境必须配置 SMTP_HOST / SMTP_USERNAME / SMTP_PASSWORD / SMTP_FROM" >&2
  exit 1
fi

echo "========================================"
echo " 生产环境启动 (profile=$SPRING_PROFILES_ACTIVE)"
echo "========================================"

"$SCRIPT_DIR/../docker/start.sh"
SYNC_FORCE=1 "$SCRIPT_DIR/../db/sync-mysql.sh"

kill_port 8080
kill_port 8081

cd "$PROJECT_ROOT"
echo "[构建] 安装 gateway、user-service..."
mvn install -pl gateway,service/user-service -am -DskipTests -q

echo "[启动] user-service (8081)..."
start_spring_service service/user-service user-service 8081 background

echo "[等待] user-service 注册..."
sleep 8

echo "[启动] gateway (8080)..."
start_spring_service gateway gateway 8080 background

echo ""
echo "========================================"
echo " 后端已启动"
echo " 网关:     http://localhost:8080  (生产请走 HTTPS 反代)"
echo " 用户服务: http://localhost:8081"
echo ""
echo " 前端构建:"
echo "   cd game-community && cp .env.production .env.production.local"
echo "   修改 REACT_APP_BASE_URL 后执行 npm run build"
echo "   将 build/ 目录部署到 Nginx/CDN"
echo "========================================"
