#!/bin/bash
# 启动 steam-service：清理端口 8083 后启动

set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "$SCRIPT_DIR/../lib/common.sh"

RUN_MODE="${1:-foreground}" # foreground | background

start_spring_service "service/steam-service" "steam-service" "8083" "$RUN_MODE"
