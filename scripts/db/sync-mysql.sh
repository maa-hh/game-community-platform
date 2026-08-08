#!/bin/bash
# 同步 MySQL 表结构：按 migrations.order 执行 sql/ 脚本
# - 首次执行：写入 _schema_migrations
# - 已执行脚本内容变更：阻断并要求新增迁移文件，不自动重跑
# - 已执行脚本禁止通过本同步器执行 DROP TABLE 等破坏性操作

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "$SCRIPT_DIR/../lib/common.sh"

load_env
require_command docker

ORDER_FILE="$SCRIPT_DIR/migrations.order"
SQL_DIR="${PROJECT_ROOT}/sql"

if [[ ! -f "$ORDER_FILE" ]]; then
  echo "缺少迁移顺序文件: $ORDER_FILE" >&2
  exit 1
fi

"$SCRIPT_DIR/wait-mysql.sh"

# 客户端字符集须为 utf8mb4，否则 SQL 文件中的中文会以 latin1 误解码写入（前端显示乱码）
MYSQL_CHARSET_ARGS=(--default-character-set=utf8mb4)

mysql_exec() {
  docker exec -i "$MYSQL_CONTAINER" mysql \
    "${MYSQL_CHARSET_ARGS[@]}" \
    -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DB" "$@"
}

mysql_query() {
  docker exec "$MYSQL_CONTAINER" mysql \
    "${MYSQL_CHARSET_ARGS[@]}" \
    -N -s \
    -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DB" \
    "$@"
}

MIGRATION_LOCK_NAME="game_community_schema_migrations"
MIGRATION_LOCK_RESULT="$(mysql_query -e "SELECT GET_LOCK('${MIGRATION_LOCK_NAME}', 30);" 2>/dev/null || true)"
if [[ "$MIGRATION_LOCK_RESULT" != "1" ]]; then
  echo "无法获得数据库迁移锁，已有迁移正在执行或数据库不可用" >&2
  exit 1
fi
release_migration_lock() {
  mysql_query -e "SELECT RELEASE_LOCK('${MIGRATION_LOCK_NAME}');" >/dev/null 2>&1 || true
}
trap release_migration_lock EXIT

echo "========================================"
echo " MySQL 结构同步 -> ${MYSQL_DB}"
echo "========================================"

mysql_exec <<'SQL'
CREATE TABLE IF NOT EXISTS _schema_migrations (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  script_name VARCHAR(255) NOT NULL UNIQUE,
  checksum VARCHAR(64) NOT NULL,
  applied_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SQL迁移记录';
SQL

apply_sql_file() {
  local file_name="$1"
  local file_path="${SQL_DIR}/${file_name}"

  if [[ ! -f "$file_path" ]]; then
    echo "[跳过] 文件不存在: $file_name"
    return 0
  fi

  local checksum stored
  checksum="$(md5 -q "$file_path" 2>/dev/null || md5sum "$file_path" | awk '{print $1}')"
  stored="$(mysql_query -e "SELECT checksum FROM _schema_migrations WHERE script_name='${file_name}' LIMIT 1;" 2>/dev/null || true)"

  if [[ -n "$stored" && "$stored" == "$checksum" ]]; then
    echo "[跳过] 已同步且无变更: $file_name"
    return 0
  fi

  if [[ -n "$stored" && "$stored" != "$checksum" ]]; then
    echo "[阻断] 已执行脚本发生变更: $file_name" >&2
    echo "       请新增独立迁移文件，禁止修改后自动重跑原脚本。" >&2
    return 1
  fi

  echo "[执行] $file_name"

  mysql_exec <"$file_path"

  mysql_exec <<SQL
INSERT INTO _schema_migrations (script_name, checksum)
VALUES ('${file_name}', '${checksum}')
ON DUPLICATE KEY UPDATE checksum='${checksum}', updated_at=CURRENT_TIMESTAMP;
SQL
}

while IFS= read -r file_name; do
  [[ -z "$file_name" || "$file_name" =~ ^# ]] && continue
  apply_sql_file "$file_name"
done < "$ORDER_FILE"

echo "MySQL 同步完成"
