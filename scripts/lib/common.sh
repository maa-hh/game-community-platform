#!/bin/bash
# 公共函数：环境加载、端口清理、Spring Boot 启动

set -euo pipefail

resolve_project_root() {
  local dir="$1"
  while [[ "$dir" != "/" ]]; do
    if [[ -f "$dir/pom.xml" && -f "$dir/docker-compose.yml" ]]; then
      echo "$dir"
      return 0
    fi
    dir="$(dirname "$dir")"
  done
  echo "无法定位项目根目录（需要 pom.xml 与 docker-compose.yml）" >&2
  return 1
}

if [[ -z "${PROJECT_ROOT:-}" ]]; then
  PROJECT_ROOT="$(resolve_project_root "$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)")"
fi

LOG_DIR="${PROJECT_ROOT}/logs"
PID_DIR="${PROJECT_ROOT}/.pids"
ENV_FILE="${PROJECT_ROOT}/.env"

mkdir -p "$LOG_DIR" "$PID_DIR"

load_env() {
  if [[ -f "$ENV_FILE" ]]; then
    set -a
    # shellcheck disable=SC1090
    source "$ENV_FILE"
    set +a
  fi

  export MAVEN_HOME="${MAVEN_HOME:-$HOME/.local/tools/apache-maven-3.9.16}"
  if [[ -d "$MAVEN_HOME/bin" ]]; then
    export PATH="$MAVEN_HOME/bin:$PATH"
  fi

  export MYSQL_HOST="${MYSQL_HOST:-localhost}"
  export MYSQL_PORT="${MYSQL_PORT:-3307}"
  export MYSQL_DB="${MYSQL_DB:-game_community}"
  export MYSQL_USER="${MYSQL_USER:-root}"
  export MYSQL_PASSWORD="${MYSQL_PASSWORD:-root123}"
  export MYSQL_CONTAINER="${MYSQL_CONTAINER:-mysql}"
  export NACOS_DISCOVERY_IP="${NACOS_DISCOVERY_IP:-127.0.0.1}"
}

require_command() {
  local cmd="$1"
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "缺少命令: $cmd" >&2
    exit 1
  fi
}

kill_port() {
  local port="$1"
  local pids=""

  if ! [[ "$port" =~ ^[0-9]+$ ]]; then
    echo "无效端口: $port" >&2
    return 1
  fi

  pids="$(lsof -ti tcp:"$port" -sTCP:LISTEN 2>/dev/null || true)"
  if [[ -z "$pids" ]]; then
    pids="$(lsof -ti tcp:"$port" 2>/dev/null || true)"
  fi

  if [[ -n "$pids" ]]; then
    echo "[端口清理] $port -> PID: $pids"
    # shellcheck disable=SC2086
    kill -15 $pids 2>/dev/null || true
    sleep 1
    # shellcheck disable=SC2086
    kill -9 $pids 2>/dev/null || true
  else
    echo "[端口清理] $port 未被占用"
  fi
}

build_java_opts() {
  load_env
  local opts=""
  if [[ -n "${ALIBABA_CLOUD_ACCESS_KEY_ID:-}" ]]; then
    opts="-Dalibaba.cloud.accessKeyId=${ALIBABA_CLOUD_ACCESS_KEY_ID}"
  fi
  if [[ -n "${ALIBABA_CLOUD_ACCESS_KEY_SECRET:-}" ]]; then
    opts="$opts -Dalibaba.cloud.accessKeySecret=${ALIBABA_CLOUD_ACCESS_KEY_SECRET}"
  fi
  # 本地开发固定注册 127.0.0.1，避免网卡 IP（如 10.x）在 Gateway 侧不可达导致请求超时
  opts="$opts -Dspring.cloud.nacos.discovery.ip=${NACOS_DISCOVERY_IP}"
  echo "$opts"
}

start_spring_service() {
  local module="$1"
  local service_name="$2"
  local port="$3"
  local run_mode="${4:-foreground}" # foreground | background

  load_env
  require_command mvn

  kill_port "$port"

  local java_opts
  java_opts="$(build_java_opts)"
  local profile_args=()
  if [[ -n "${SPRING_PROFILES_ACTIVE:-}" ]]; then
    profile_args=(-Dspring-boot.run.arguments="--spring.profiles.active=${SPRING_PROFILES_ACTIVE}")
  fi
  local log_file="${LOG_DIR}/${service_name}.log"
  local pid_file="${PID_DIR}/${service_name}.pid"

  cd "$PROJECT_ROOT"

  if [[ "${SKIP_BUILD:-0}" == "1" ]]; then
    echo "[构建] 跳过 (SKIP_BUILD=1)"
  else
    echo "[构建] 清理并安装 ${module} 及依赖到本地仓库（避免残留 class 导致启动失败）..."
    mvn clean install -pl "$module" -am -DskipTests -q
  fi

  echo "[启动] ${service_name} (module=${module}, port=${port})"

  # profile_args 可能为空；在 set -u 下需用 ${arr[@]+"${arr[@]}"} 展开
  if [[ "$run_mode" == "background" ]]; then
    nohup mvn spring-boot:run \
      -pl "$module" \
      -DskipTests \
      -Dspring-boot.run.jvmArguments="$java_opts" \
      ${profile_args[@]+"${profile_args[@]}"} \
      >"$log_file" 2>&1 &
    echo $! >"$pid_file"
    echo "[后台] ${service_name} PID=$(cat "$pid_file"), 日志: $log_file"
  else
    mvn spring-boot:run \
      -pl "$module" \
      -DskipTests \
      -Dspring-boot.run.jvmArguments="$java_opts" \
      ${profile_args[@]+"${profile_args[@]}"}
  fi
}

stop_service_by_name() {
  local service_name="$1"
  local pid_file="${PID_DIR}/${service_name}.pid"
  if [[ -f "$pid_file" ]]; then
    local pid
    pid="$(cat "$pid_file")"
    if kill -0 "$pid" 2>/dev/null; then
      echo "[停止] ${service_name} PID=${pid}"
      kill -15 "$pid" 2>/dev/null || true
      sleep 1
      kill -9 "$pid" 2>/dev/null || true
    fi
    rm -f "$pid_file"
  fi
}
