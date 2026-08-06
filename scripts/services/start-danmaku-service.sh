#!/bin/bash
# 启动 danmaku-service：清理端口 8094 后启动

set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "$SCRIPT_DIR/../lib/common.sh"
# shellcheck disable=SC1091
source "$SCRIPT_DIR/../lib/parse-services.sh"

RUN_MODE="${1:-foreground}" # foreground | background

start_spring_service "service/danmaku-service" "danmaku-service" "8094" "$RUN_MODE"
