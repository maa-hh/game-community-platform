#!/bin/bash
# 启动 Docker 基础设施：不存在则创建，已停止则拉起

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "$SCRIPT_DIR/../lib/common.sh"

load_env
require_command docker

cd "$PROJECT_ROOT"

echo "========================================"
echo " Docker 基础设施启动"
echo "========================================"

if docker compose version >/dev/null 2>&1; then
  COMPOSE=(docker compose)
elif docker-compose version >/dev/null 2>&1; then
  COMPOSE=(docker-compose)
else
  echo "未找到 docker compose，请先安装 Docker Desktop" >&2
  exit 1
fi

echo "[1/2] 拉取/构建镜像并启动容器..."
"${COMPOSE[@]}" up -d --remove-orphans

echo "[2/2] 等待核心服务就绪..."
"$SCRIPT_DIR/../db/wait-mysql.sh"

echo "Docker 服务状态:"
"${COMPOSE[@]}" ps
