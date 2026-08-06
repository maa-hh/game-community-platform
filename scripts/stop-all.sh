#!/bin/bash
# 停止全部微服务：按 PID 文件 + 端口双重清理

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "$SCRIPT_DIR/lib/common.sh"
# shellcheck disable=SC1091
source "$SCRIPT_DIR/lib/parse-services.sh"

CONF_FILE="$SCRIPT_DIR/lib/services.conf"
load_services "$CONF_FILE"

echo "========================================"
echo " 停止全部微服务"
echo "========================================"

for name in "${SERVICES[@]}"; do
  stop_service_by_name "$name"
done

for port in "${SERVICE_PORTS[@]}"; do
  kill_port "$port"
done

echo "全部微服务已停止"
