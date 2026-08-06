#!/bin/bash
# 生成单个微服务启动脚本

set -euo pipefail

if [[ $# -lt 3 ]]; then
  echo "用法: $0 <service-name> <maven-module> <port>" >&2
  exit 1
fi

SERVICE_NAME="$1"
MODULE="$2"
PORT="$3"
TARGET_DIR="$(cd "$(dirname "$0")/.." && pwd)/services"

mkdir -p "$TARGET_DIR"

cat > "${TARGET_DIR}/start-${SERVICE_NAME}.sh" <<EOF
#!/bin/bash
# 启动 ${SERVICE_NAME}：清理端口 ${PORT} 后启动

set -euo pipefail
SCRIPT_DIR="\$(cd "\$(dirname "\${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "\$SCRIPT_DIR/../lib/common.sh"
# shellcheck disable=SC1091
source "\$SCRIPT_DIR/../lib/parse-services.sh"

RUN_MODE="\${1:-foreground}" # foreground | background

start_spring_service "${MODULE}" "${SERVICE_NAME}" "${PORT}" "\$RUN_MODE"
EOF

chmod +x "${TARGET_DIR}/start-${SERVICE_NAME}.sh"
echo "生成: ${TARGET_DIR}/start-${SERVICE_NAME}.sh"
