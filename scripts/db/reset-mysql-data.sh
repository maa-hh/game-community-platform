#!/bin/bash
# 暴力覆盖 MySQL 业务数据：清空迁移记录并按 migrations.order 重跑全部 SQL
# 用法：./scripts/db/reset-mysql-data.sh
# 等价于：SYNC_FORCE=1 ./scripts/db/sync-mysql.sh（会先等待 MySQL 就绪）

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "$SCRIPT_DIR/../lib/common.sh"

load_env
require_command docker

echo "========================================"
echo " 暴力覆盖 MySQL 数据（DROP + 重建表）"
echo " 数据库: ${MYSQL_DB} @ ${MYSQL_HOST}:${MYSQL_PORT}"
echo "========================================"

"$SCRIPT_DIR/wait-mysql.sh"

SYNC_FORCE=1 "$SCRIPT_DIR/sync-mysql.sh"

echo "MySQL 数据已暴力重置完成"
