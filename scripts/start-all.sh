#!/bin/bash
# 一键启动：Docker + MySQL同步 + 清理端口 + 启动全部微服务

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "$SCRIPT_DIR/lib/common.sh"
# shellcheck disable=SC1091
source "$SCRIPT_DIR/lib/parse-services.sh"

CONF_FILE="$SCRIPT_DIR/lib/services.conf"
START_DOCKER="${START_DOCKER:-1}"
SYNC_DB="${SYNC_DB:-1}"
START_INTERVAL="${START_INTERVAL:-4}"

load_services "$CONF_FILE"

echo "========================================"
echo " 游戏社区平台 - 全量启动"
echo " 项目目录: $PROJECT_ROOT"
echo "========================================"

if [[ "$START_DOCKER" == "1" ]]; then
  "$SCRIPT_DIR/docker/start.sh"
fi

if [[ "$SYNC_DB" == "1" ]]; then
  "$SCRIPT_DIR/db/sync-mysql.sh"
fi

if [[ "${SKIP_BUILD:-0}" == "1" ]]; then
  echo "[构建] 跳过全仓构建 (SKIP_BUILD=1)"
else
  echo "[构建] 全仓共享模块只构建一次，避免后台服务互相清理 target..."
  cd "$PROJECT_ROOT"
  mvn clean install -DskipTests -q
  export BUILD_ALREADY_DONE=1
fi

echo ""
echo "========================================"
echo " 清理微服务端口并后台启动"
echo "========================================"

for port in "${SERVICE_PORTS[@]}"; do
  kill_port "$port"
done

# 业务服务先启动，网关最后（等待注册）
gateway_idx="$(get_service_index gateway)"
for i in "${!SERVICES[@]}"; do
  [[ "$i" == "$gateway_idx" ]] && continue
  name="${SERVICES[$i]}"
  module="${SERVICE_MODULES[$i]}"
  port="${SERVICE_PORTS[$i]}"
  start_spring_service "$module" "$name" "$port" background
  sleep "$START_INTERVAL"
done

if [[ -n "${gateway_idx:-}" ]]; then
  echo "[等待] 网关启动前等待 ${START_INTERVAL}s ..."
  sleep "$START_INTERVAL"
  start_spring_service "${SERVICE_MODULES[$gateway_idx]}" gateway "${SERVICE_PORTS[$gateway_idx]}" background
fi

echo ""
echo "========================================"
echo " 全部微服务已在后台启动"
echo " 日志目录: $LOG_DIR"
echo " PID 目录: $PID_DIR"
echo ""
echo " 网关:     http://localhost:8080"
echo " 用户服务: http://localhost:8081"
echo " 弹幕服务: http://localhost:8094"
echo " 单独启动: ./scripts/services/start-danmaku-service.sh [foreground|background]"
echo " 停止全部: ./scripts/stop-all.sh"
echo "========================================"
