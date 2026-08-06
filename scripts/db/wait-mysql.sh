#!/bin/bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "$SCRIPT_DIR/../lib/common.sh"

load_env

max_retry=60
retry=0

echo "等待 MySQL 就绪 (${MYSQL_CONTAINER}:${MYSQL_PORT})..."

while (( retry < max_retry )); do
  if docker exec "$MYSQL_CONTAINER" mysqladmin ping \
      -h 127.0.0.1 -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" --silent >/dev/null 2>&1; then
    echo "MySQL 已就绪"
    exit 0
  fi
  retry=$((retry + 1))
  sleep 2
done

echo "MySQL 启动超时，请检查容器: docker logs ${MYSQL_CONTAINER}" >&2
exit 1
