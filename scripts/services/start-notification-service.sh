#!/bin/bash
# 启动 notification-service：清理端口 8092 后启动

set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "$SCRIPT_DIR/../lib/common.sh"
# shellcheck disable=SC1091
source "$SCRIPT_DIR/../lib/parse-services.sh"

RUN_MODE="${1:-foreground}" # foreground | background

start_spring_service "service/notification-service" "notification-service" "8092" "$RUN_MODE"
