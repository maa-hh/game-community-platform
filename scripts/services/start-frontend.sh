#!/bin/bash
# 启动 frontend（React + CRA）：构建生产包、清理端口/PID、启动静态站点与 API 代理

set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "$SCRIPT_DIR/../lib/common.sh"

RUN_MODE="${1:-foreground}" # foreground | background
FRONTEND_DIR="${FRONTEND_DIR:-}"
FRONTEND_PORT="${FRONTEND_PORT:-3000}"
BACKEND_URL="${BACKEND_URL:-http://127.0.0.1:8080}"
SERVICE_NAME="frontend"
PID_FILE="${PID_DIR}/${SERVICE_NAME}.pid"
LOG_FILE="${LOG_DIR}/${SERVICE_NAME}.log"

resolve_frontend_dir() {
  if [[ -n "$FRONTEND_DIR" ]]; then
    return 0
  fi

  local candidate
  for candidate in \
    "${PROJECT_ROOT}/frontend" \
    "${PROJECT_ROOT}/../../reactproject/game-community" \
    "${HOME}/reactproject/game-community"; do
    if [[ -f "${candidate}/package.json" ]]; then
      FRONTEND_DIR="$(cd "$candidate" && pwd)"
      return 0
    fi
  done

  FRONTEND_DIR="${PROJECT_ROOT}/frontend"
}

ensure_node_path() {
  local node_home
  for node_home in \
    "${NODE_HOME:-}" \
    "$HOME/.local/tools/node-v22.14.0-darwin-arm64" \
    "$HOME/.local/tools/node-v22.14.0-darwin-x64"; do
    if [[ -n "$node_home" && -x "$node_home/bin/node" ]]; then
      export PATH="$node_home/bin:$PATH"
      return 0
    fi
  done
  local cursor_node="/Applications/Cursor.app/Contents/Resources/app/resources/helpers/node"
  if [[ -x "$cursor_node" ]]; then
    export PATH="$(dirname "$cursor_node"):$PATH"
  fi
}

require_node() {
  ensure_node_path
  if ! command -v node >/dev/null 2>&1; then
    echo "缺少 Node.js。请安装 Node 20+：https://nodejs.org/" >&2
    exit 1
  fi
  local major
  major="$(node -p "process.versions.node.split('.')[0]")"
  if [[ "$major" -lt 18 ]]; then
    echo "Node 版本过低（当前 $(node -v)），建议 Node 20+" >&2
    exit 1
  fi
}

require_npm() {
  if ! command -v npm >/dev/null 2>&1; then
    echo "缺少 npm，请随 Node.js 一并安装。" >&2
    exit 1
  fi
}

stop_frontend_pid() {
  stop_service_by_name "$SERVICE_NAME"
}

ensure_dependencies() {
  cd "$FRONTEND_DIR"
  if [[ ! -d node_modules ]] || [[ ! -d node_modules/react ]] || [[ ! -d node_modules/http-proxy ]]; then
    echo "[依赖] 安装 npm 包（首次或 node_modules 缺失）..."
    npm install
  else
    echo "[依赖] node_modules 已存在，跳过 npm install（如需强制重装请删除 frontend/node_modules）"
  fi
}

build_frontend() {
  cd "$FRONTEND_DIR"
  echo "[构建] frontend production bundle"
  npm run build
}

start_production_server() {
  cd "$FRONTEND_DIR"

  echo "[启动] frontend production server → http://127.0.0.1:${FRONTEND_PORT}"
  echo "[代理] API → ${BACKEND_URL}"

  if [[ "$RUN_MODE" == "background" ]]; then
    nohup env PORT="$FRONTEND_PORT" BACKEND_URL="$BACKEND_URL" \
      npm run serve:production >"$LOG_FILE" 2>&1 &
    echo $! >"$PID_FILE"
    echo "[后台] frontend PID=$(cat "$PID_FILE"), 日志: $LOG_FILE"
  else
    PORT="$FRONTEND_PORT" BACKEND_URL="$BACKEND_URL" npm run serve:production
  fi
}

main() {
  load_env
  require_node
  require_npm
  resolve_frontend_dir

  if [[ ! -d "$FRONTEND_DIR" ]]; then
    echo "frontend 目录不存在: $FRONTEND_DIR" >&2
    exit 1
  fi

  echo "[清理] 停止已有 frontend 进程..."
  stop_frontend_pid
  kill_port "$FRONTEND_PORT"

  ensure_dependencies
  build_frontend
  start_production_server
}

main "$@"
