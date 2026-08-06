#!/bin/bash
# 仅清理全部微服务端口（不启动）

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "$SCRIPT_DIR/lib/common.sh"
# shellcheck disable=SC1091
source "$SCRIPT_DIR/lib/parse-services.sh"

CONF_FILE="$SCRIPT_DIR/lib/services.conf"
load_services "$CONF_FILE"

echo "清理微服务端口..."
for port in "${SERVICE_PORTS[@]}"; do
  kill_port "$port"
done
echo "完成"
