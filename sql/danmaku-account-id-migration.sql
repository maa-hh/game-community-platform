-- 弹幕对外身份字段迁移：事件和响应只使用 account_id，user_id 仅保留为数据库内部关联。
SET @has_danmaku_account_column := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 't_danmaku_message'
      AND column_name = 'account_id'
);
SET @add_danmaku_account_column_sql := IF(
    @has_danmaku_account_column = 0,
    'ALTER TABLE t_danmaku_message ADD COLUMN account_id BIGINT NULL COMMENT ''对外账号ID''',
    'SELECT 1'
);
PREPARE add_danmaku_account_column FROM @add_danmaku_account_column_sql;
EXECUTE add_danmaku_account_column;
DEALLOCATE PREPARE add_danmaku_account_column;

UPDATE t_danmaku_message message
JOIN t_user user_record ON user_record.id = message.user_id
SET message.account_id = user_record.account_id
WHERE message.account_id IS NULL;

ALTER TABLE t_danmaku_message
    MODIFY account_id BIGINT NOT NULL COMMENT '对外账号ID';

SET @has_danmaku_account_index := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 't_danmaku_message'
      AND index_name = 'idx_danmaku_account_time'
);
SET @add_danmaku_account_index_sql := IF(
    @has_danmaku_account_index = 0,
    'ALTER TABLE t_danmaku_message ADD KEY idx_danmaku_account_time (account_id, create_time)',
    'SELECT 1'
);
PREPARE add_danmaku_account_index FROM @add_danmaku_account_index_sql;
EXECUTE add_danmaku_account_index;
DEALLOCATE PREPARE add_danmaku_account_index;
