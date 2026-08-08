-- 用户服务 v2 数据规范迁移
-- 目的：把已存在数据收敛到当前非空字段和并发处理格式；不保留旧接口/旧字段兼容分支。

UPDATE t_user
SET steam_account = ''
WHERE steam_account IS NULL;

ALTER TABLE t_user
    MODIFY steam_account VARCHAR(64) NOT NULL DEFAULT '' COMMENT 'Steam账号';

UPDATE t_user_profile_audit
SET pending_username = COALESCE(pending_username, ''),
    pending_signature = COALESCE(pending_signature, ''),
    pending_avatar = COALESCE(pending_avatar, ''),
    version = COALESCE(version, 0);

UPDATE t_user_auth
SET salt = COALESCE(salt, '');

ALTER TABLE t_user_auth
    MODIFY salt VARCHAR(32) NOT NULL DEFAULT '' COMMENT '盐（BCrypt留空）';

UPDATE t_user_account
SET ban_reason = COALESCE(ban_reason, ''),
    last_login_ip = COALESCE(last_login_ip, '');

ALTER TABLE t_user_account
    MODIFY ban_reason VARCHAR(255) NOT NULL DEFAULT '',
    MODIFY last_login_ip VARCHAR(45) NOT NULL DEFAULT '';

UPDATE t_user_audit_task
SET pending_content = COALESCE(pending_content, ''),
    error_message = COALESCE(error_message, '');

UPDATE t_user_audit_task task
JOIN t_user user_record ON user_record.id = task.user_id
SET task.payload = JSON_SET(
        CAST(task.payload AS JSON),
        '$.userVersion',
        COALESCE(user_record.version, 0)
    )
WHERE JSON_VALID(task.payload)
  AND JSON_EXTRACT(task.payload, '$.userVersion') IS NULL;

UPDATE t_user_audit_reject_log reject_log
JOIN t_user user_record ON user_record.id = reject_log.user_id
SET reject_log.account_id = user_record.account_id
WHERE reject_log.account_id IS NULL;

ALTER TABLE t_user_audit_reject_log
    MODIFY account_id BIGINT NOT NULL COMMENT '帐号ID';

-- 新索引覆盖恢复扫描、用户字段任务查询；旧前缀索引已无独立查询价值。
SET @has_old_pool_index := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 't_account_id_pool'
      AND index_name = 'idx_status_digit'
);
SET @drop_old_pool_index_sql := IF(
    @has_old_pool_index > 0,
    'ALTER TABLE t_account_id_pool DROP INDEX idx_status_digit',
    'SELECT 1'
);
PREPARE drop_old_pool_index FROM @drop_old_pool_index_sql;
EXECUTE drop_old_pool_index;
DEALLOCATE PREPARE drop_old_pool_index;

SET @has_new_pool_index := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 't_account_id_pool'
      AND index_name = 'idx_status_digit_account'
);
SET @add_new_pool_index_sql := IF(
    @has_new_pool_index = 0,
    'ALTER TABLE t_account_id_pool ADD KEY idx_status_digit_account (status, digit_count, account_id)',
    'SELECT 1'
);
PREPARE add_new_pool_index FROM @add_new_pool_index_sql;
EXECUTE add_new_pool_index;
DEALLOCATE PREPARE add_new_pool_index;

SET @has_user_time_index := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 't_user_audit_task'
      AND index_name = 'idx_audit_task_user_time'
);
SET @add_user_time_index_sql := IF(
    @has_user_time_index = 0,
    'ALTER TABLE t_user_audit_task ADD KEY idx_audit_task_user_time (user_id, task_type, create_time, id)',
    'SELECT 1'
);
PREPARE add_user_time_index FROM @add_user_time_index_sql;
EXECUTE add_user_time_index;
DEALLOCATE PREPARE add_user_time_index;

SET @has_old_audit_time_index := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 't_user_audit_task'
      AND index_name = 'idx_create_time'
);
SET @drop_old_audit_time_index_sql := IF(
    @has_old_audit_time_index > 0,
    'ALTER TABLE t_user_audit_task DROP INDEX idx_create_time',
    'SELECT 1'
);
PREPARE drop_old_audit_time_index FROM @drop_old_audit_time_index_sql;
EXECUTE drop_old_audit_time_index;
DEALLOCATE PREPARE drop_old_audit_time_index;
