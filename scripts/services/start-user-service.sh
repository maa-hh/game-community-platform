#!/bin/bash
# 启动 user-service：清理端口 8081 后启动

set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "$SCRIPT_DIR/../lib/common.sh"
# shellcheck disable=SC1091
source "$SCRIPT_DIR/../lib/parse-services.sh"

RUN_MODE="${1:-foreground}" # foreground | background

start_spring_service "service/user-service" "user-service" "8081" "$RUN_MODE"
