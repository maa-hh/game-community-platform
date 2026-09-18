#!/usr/bin/env bash
# 分阶段启动验证 AI Agent 的并发、Provider、超时、输入和监控改造。

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
STAGE="${1:?用法: $0 stage1|stage2|stage3|stage4|stage5|stage6}"
LOG_DIR="${ROOT_DIR}/logs/verify-ai-hardening"
mkdir -p "$LOG_DIR"
APP_PIDS=()
STUB_PID=""
CONTENT_PID=""
COUNT_FILE="$(mktemp)"

cleanup() {
  set +e
  for pid in "${APP_PIDS[@]:-}"; do
    [[ -n "$pid" ]] && kill "$pid" 2>/dev/null || true
  done
  for port in 18093 18094; do
    for pid in $(lsof -ti tcp:"$port" 2>/dev/null || true); do
      kill "$pid" 2>/dev/null || true
    done
  done
  [[ -n "$CONTENT_PID" ]] && kill "$CONTENT_PID" 2>/dev/null || true
  for pid in $(lsof -ti tcp:18082 2>/dev/null || true); do
    kill "$pid" 2>/dev/null || true
  done
  [[ -n "$STUB_PID" ]] && kill "$STUB_PID" 2>/dev/null || true
  rm -f "$COUNT_FILE"
}
trap cleanup EXIT

wait_http() {
  local port="$1" path="$2"
  for _ in $(seq 1 60); do
    if curl -fsS "http://127.0.0.1:${port}${path}" >/dev/null 2>&1; then return 0; fi
    sleep 1
  done
  echo "服务未在端口 ${port} 启动，日志：${LOG_DIR}/ai-${port}.log" >&2
  tail -80 "${LOG_DIR}/ai-${port}.log" >&2 || true
  return 1
}

build_app() {
  (cd "$ROOT_DIR" && mvn -q -pl service/ai-agent-service -am package -DskipTests)
}

build_content() {
  (cd "$ROOT_DIR" && mvn -q -pl service/content-service -am package -DskipTests)
}

wait_port() {
  local port="$1" log_file="$2"
  for _ in $(seq 1 90); do
    if [[ "$(curl -sS -o /dev/null -w '%{http_code}' "http://127.0.0.1:${port}/" || true)" != "000" ]]; then
      return 0
    fi
    sleep 1
  done
  echo "服务未在端口 ${port} 启动，日志：${log_file}" >&2
  tail -100 "$log_file" >&2 || true
  return 1
}

start_content() {
  SERVER_PORT=18082 NACOS_CONFIG_ENABLED=false \
  SPRING_CLOUD_NACOS_DISCOVERY_ENABLED=false KAFKA_SERVERS=localhost:9093 \
  CONTENT_LOG_LEVEL=INFO mvn -q -pl service/content-service spring-boot:run -DskipTests \
  >"${LOG_DIR}/content-18082.log" 2>&1 &
  CONTENT_PID=$!
  wait_port 18082 "${LOG_DIR}/content-18082.log"
  grep -q "Started ContentServiceApplication" "${LOG_DIR}/content-18082.log"
}

start_stub() {
  python3 "$ROOT_DIR/scripts/verify/openai-compatible-stub.py" \
    --port 19090 --count-file "$COUNT_FILE" --delay 2 \
    >"${LOG_DIR}/model-stub.log" 2>&1 &
  STUB_PID=$!
  for _ in $(seq 1 20); do
    if [[ "$(curl -sS -o /dev/null -w '%{http_code}' \
      "http://127.0.0.1:19090/v1/chat/completions" || true)" == "501" ]]; then return 0; fi
    sleep 0.2
  done
  echo "模型桩启动失败" >&2
  return 1
}

start_app() {
  local port="$1" global_limit="$2" read_timeout="${3:-10000}"
  local max_image_bytes="${4:-5242880}" max_article_images="${5:-9}" allow_remote_urls="${6:-false}"
  SERVER_PORT="$port" NACOS_CONFIG_ENABLED=false \
  SPRING_CLOUD_NACOS_DISCOVERY_ENABLED=false REDIS_HOST=127.0.0.1 REDIS_PORT=6380 \
  DEEPSEEK_BASE_URL=http://127.0.0.1:19090 DEEPSEEK_API_KEY=verification-key \
  DEEPSEEK_COMPLETIONS_PATH=/v1/chat/completions \
  DEEPSEEK_CHAT_MODEL=verification-model DEEPSEEK_TEXT_MODEL=verification-model \
  DEEPSEEK_IMAGE_MODEL=verification-model AUDIT_MAX_CONCURRENT_MODEL_CALLS=1 \
  AUDIT_GLOBAL_MAX_CONCURRENT_MODEL_CALLS="$global_limit" AI_SPRINGDOC_ENABLED=true \
  DEEPSEEK_READ_TIMEOUT_MS="$read_timeout" \
  AUDIT_MAX_IMAGE_BYTES="$max_image_bytes" AUDIT_MAX_ARTICLE_IMAGES="$max_article_images" \
  AUDIT_ALLOW_REMOTE_IMAGE_URLS="$allow_remote_urls" \
  AI_LOG_LEVEL=INFO mvn -q -pl service/ai-agent-service spring-boot:run -DskipTests \
  >"${LOG_DIR}/ai-${port}.log" 2>&1 &
  APP_PIDS+=("$!")
  wait_http "$port" "/v3/api-docs"
}

request() {
  local port="$1" output="$2"
  curl -fsS --max-time 20 "http://127.0.0.1:${port}/feign/ai/moderation" \
    -H 'Content-Type: application/json' \
    -d '{"type":"TEXT","content":"这是启动验证请求"}' >"$output"
}

request_file() {
  local port="$1" input="$2" output="$3"
  curl -fsS --max-time 20 "http://127.0.0.1:${port}/feign/ai/moderation" \
    -H 'Content-Type: application/json' --data-binary "@$input" >"$output"
}

assert_result() {
  python3 - "$1" "$2" <<'PY'
import json
import sys
data = json.load(open(sys.argv[1], encoding="utf-8"))
actual = data.get("data", {}).get("result")
if actual != sys.argv[2]:
    raise SystemExit(f"期望 {sys.argv[2]}，实际 {actual}: {data}")
PY
}

now_ms() {
  python3 -c 'import time; print(time.monotonic_ns() // 1000000)'
}

build_app

case "$STAGE" in
  stage1)
    docker exec redis redis-cli -p 6379 DEL ai-agent:moderation:concurrency:deepseek >/dev/null
    start_stub
    start_app 18093 1
    start_app 18094 1
    request 18093 "${LOG_DIR}/response-18093.json" & p1=$!
    sleep 0.3
    request 18094 "${LOG_DIR}/response-18094.json" & p2=$!
    wait "$p1"; wait "$p2"
    assert_result "${LOG_DIR}/response-18093.json" "PASS"
    assert_result "${LOG_DIR}/response-18094.json" "HUMAN_REVIEW"
    [[ "$(cat "$COUNT_FILE")" == "1" ]] || { echo "模型桩收到的请求数不是 1" >&2; exit 1; }
    echo "stage1 PASS: 两个 AI 实例共享 Redis 全局并发许可。"
    ;;
  stage2)
    start_stub
    start_app 18093 32
    request 18093 "${LOG_DIR}/response-stage.json"
    assert_result "${LOG_DIR}/response-stage.json" "PASS"
    echo "${STAGE} PASS: AI 服务真实启动并完成 OpenAI Compatible 审核请求。"
    ;;
  stage6)
    start_stub
    start_app 18093 32
    request 18093 "${LOG_DIR}/response-metrics.json"
    assert_result "${LOG_DIR}/response-metrics.json" "PASS"
    curl -fsS "http://127.0.0.1:18093/actuator/prometheus" >"${LOG_DIR}/metrics.txt"
    grep -q "ai_moderation_requests_total" "${LOG_DIR}/metrics.txt"
    grep -q "ai_provider_calls_total" "${LOG_DIR}/metrics.txt"
    grep -q "ai_moderation_duration_seconds" "${LOG_DIR}/metrics.txt"
    echo "stage6 PASS: Prometheus 已暴露审核请求、Provider 调用和耗时指标。"
    ;;
  stage5)
    build_content
    start_content
    echo "stage5 PASS: content-service 真实启动，AI Feign CircuitBreaker 配置已加载。"
    ;;
  stage4)
    start_stub
    start_app 18093 32 10000 4 1 false
    python3 - "${LOG_DIR}/large-image.json" "${LOG_DIR}/remote-image.json" "${LOG_DIR}/too-many-images.json" <<'PY'
import base64
import json
import sys

large = base64.b64encode(b"123456789").decode()
json.dump({"type": "IMAGE", "imageBase64": large, "mimeType": "image/png"},
          open(sys.argv[1], "w", encoding="utf-8"))
json.dump({"type": "IMAGE", "imageUrl": "http://127.0.0.1:19090/image.png"},
          open(sys.argv[2], "w", encoding="utf-8"))
image = {"imageBase64": base64.b64encode(b"1").decode(), "mimeType": "image/png"}
json.dump({"type": "ARTICLE", "content": "正文", "images": [image, image]},
          open(sys.argv[3], "w", encoding="utf-8"))
PY
    request_file 18093 "${LOG_DIR}/large-image.json" "${LOG_DIR}/response-large-image.json"
    request_file 18093 "${LOG_DIR}/remote-image.json" "${LOG_DIR}/response-remote-image.json"
    request_file 18093 "${LOG_DIR}/too-many-images.json" "${LOG_DIR}/response-too-many-images.json"
    assert_result "${LOG_DIR}/response-large-image.json" "HUMAN_REVIEW"
    assert_result "${LOG_DIR}/response-remote-image.json" "HUMAN_REVIEW"
    assert_result "${LOG_DIR}/response-too-many-images.json" "HUMAN_REVIEW"
    [[ ! -s "$COUNT_FILE" ]] || { echo "输入限制失败，模型桩收到请求: $(cat "$COUNT_FILE")" >&2; exit 1; }
    echo "stage4 PASS: 图片大小、图片数量和远程 URL 白名单均在模型调用前生效。"
    ;;
  stage3)
    start_stub
    start_app 18093 32 200
    for index in $(seq 1 10); do
      request 18093 "${LOG_DIR}/response-timeout-${index}.json" || true
      assert_result "${LOG_DIR}/response-timeout-${index}.json" "HUMAN_REVIEW"
    done
    start_time=$(now_ms)
    request 18093 "${LOG_DIR}/response-circuit.json"
    elapsed=$(( $(now_ms) - start_time ))
    assert_result "${LOG_DIR}/response-circuit.json" "HUMAN_REVIEW"
    [[ "$elapsed" -lt 1000 ]] || { echo "熔断后的第 11 次请求耗时 ${elapsed}ms，未快速失败" >&2; exit 1; }
    [[ "$(cat "$COUNT_FILE")" == "10" ]] || { echo "熔断后模型桩仍收到请求: $(cat "$COUNT_FILE")" >&2; exit 1; }
    echo "stage3 PASS: Provider 读取超时生效，连续失败后熔断并快速失败。"
    ;;
  *) echo "未知验证阶段: ${STAGE}" >&2; exit 2 ;;
esac
