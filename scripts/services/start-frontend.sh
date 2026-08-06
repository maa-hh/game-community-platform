#!/bin/bash
# 启动 frontend（React + Vite）：环境检测、依赖安装、端口/PID 清理、dev server

set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "$SCRIPT_DIR/../lib/common.sh"

RUN_MODE="${1:-foreground}" # foreground | background
FRONTEND_DIR="${PROJECT_ROOT}/frontend"
FRONTEND_PORT="${FRONTEND_PORT:-5173}"
SERVICE_NAME="frontend"
PID_FILE="${PID_DIR}/${SERVICE_NAME}.pid"
LOG_FILE="${LOG_DIR}/${SERVICE_NAME}.log"

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

ensure_env_file() {
  if [[ ! -f "${FRONTEND_DIR}/.env.development" && -f "${FRONTEND_DIR}/.env.example" ]]; then
    cp "${FRONTEND_DIR}/.env.example" "${FRONTEND_DIR}/.env.development"
    echo "[配置] 已从 .env.example 生成 .env.development"
  fi
}

ensure_dependencies() {
  cd "$FRONTEND_DIR"
  if [[ ! -d node_modules ]] || [[ ! -d node_modules/react ]]; then
    echo "[依赖] 安装 npm 包（首次或 node_modules 缺失）..."
    npm install
  else
    echo "[依赖] node_modules 已存在，跳过 npm install（如需强制重装请删除 frontend/node_modules）"
  fi
}

start_dev_server() {
  cd "$FRONTEND_DIR"
  export VITE_PROXY_TARGET="${VITE_PROXY_TARGET:-http://127.0.0.1:8080}"

  echo "[启动] frontend dev server → http://127.0.0.1:${FRONTEND_PORT}"
  echo "[代理] /api → ${VITE_PROXY_TARGET}"

  if [[ "$RUN_MODE" == "background" ]]; then
    nohup npm run dev -- --port "$FRONTEND_PORT" >"$LOG_FILE" 2>&1 &
    echo $! >"$PID_FILE"
    echo "[后台] frontend PID=$(cat "$PID_FILE"), 日志: $LOG_FILE"
  else
    npm run dev -- --port "$FRONTEND_PORT"
  fi
}

main() {
  load_env
  require_node
  require_npm

  if [[ ! -d "$FRONTEND_DIR" ]]; then
    echo "frontend 目录不存在: $FRONTEND_DIR" >&2
    exit 1
  fi

  echo "[清理] 停止已有 frontend 进程..."
  stop_frontend_pid
  kill_port "$FRONTEND_PORT"

  ensure_env_file
  ensure_dependencies
  start_dev_server
}

main "$@"
