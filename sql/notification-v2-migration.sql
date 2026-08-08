-- 通知表 v2 迁移：补齐事件幂等、聚合键和时间字段，旧数据转换为当前规范。

SET @has_event_id := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 't_notification_message'
      AND column_name = 'event_id'
);
SET @add_event_id_sql := IF(
    @has_event_id = 0,
    'ALTER TABLE t_notification_message ADD COLUMN event_id VARCHAR(96) NULL COMMENT ''上游事件唯一ID'' AFTER id',
    'SELECT 1'
);
PREPARE add_event_id FROM @add_event_id_sql;
EXECUTE add_event_id;
DEALLOCATE PREPARE add_event_id;

SET @has_aggregate_key := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 't_notification_message'
      AND column_name = 'aggregate_key'
);
SET @add_aggregate_key_sql := IF(
    @has_aggregate_key = 0,
    'ALTER TABLE t_notification_message ADD COLUMN aggregate_key VARCHAR(255) NULL COMMENT ''聚合维度键'' AFTER event_id',
    'SELECT 1'
);
PREPARE add_aggregate_key FROM @add_aggregate_key_sql;
EXECUTE add_aggregate_key;
DEALLOCATE PREPARE add_aggregate_key;

SET @has_occurred_at := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 't_notification_message'
      AND column_name = 'occurred_at'
);
SET @add_occurred_at_sql := IF(
    @has_occurred_at = 0,
    'ALTER TABLE t_notification_message ADD COLUMN occurred_at DATETIME NULL COMMENT ''业务事件发生时间'' AFTER read_time',
    'SELECT 1'
);
PREPARE add_occurred_at FROM @add_occurred_at_sql;
EXECUTE add_occurred_at;
DEALLOCATE PREPARE add_occurred_at;

UPDATE t_notification_message
SET event_id = CONCAT('legacy-', id)
WHERE event_id IS NULL OR event_id = '';

UPDATE t_notification_message
SET aggregate_key = CONCAT('legacy:', id)
WHERE aggregate_key IS NULL OR aggregate_key = '';

UPDATE t_notification_message
SET occurred_at = create_time
WHERE occurred_at IS NULL;

UPDATE t_notification_message
SET read_time = '1970-01-01 00:00:00'
WHERE read_time IS NULL;

ALTER TABLE t_notification_message
    MODIFY event_id VARCHAR(96) NOT NULL COMMENT '上游事件唯一ID',
    MODIFY aggregate_key VARCHAR(255) NOT NULL COMMENT '聚合维度键',
    MODIFY result_text TEXT NOT NULL COMMENT '结果文案或聚合参与者快照',
    MODIFY read_time DATETIME NOT NULL DEFAULT '1970-01-01 00:00:00' COMMENT '已读时间，未读为纪元时间',
    MODIFY occurred_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '业务事件发生时间';

SET @has_event_unique := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 't_notification_message'
      AND index_name = 'uk_notification_message_event'
);
SET @add_event_unique_sql := IF(
    @has_event_unique = 0,
    'ALTER TABLE t_notification_message ADD UNIQUE KEY uk_notification_message_event (user_id, event_id)',
    'SELECT 1'
);
PREPARE add_event_unique FROM @add_event_unique_sql;
EXECUTE add_event_unique;
DEALLOCATE PREPARE add_event_unique;

SET @has_old_read_index := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 't_notification_message'
      AND index_name = 'idx_notification_user_read_time'
);
SET @drop_old_read_index_sql := IF(
    @has_old_read_index > 0,
    'ALTER TABLE t_notification_message DROP INDEX idx_notification_user_read_time',
    'SELECT 1'
);
PREPARE drop_old_read_index FROM @drop_old_read_index_sql;
EXECUTE drop_old_read_index;
DEALLOCATE PREPARE drop_old_read_index;

SET @has_old_user_index := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 't_notification_message'
      AND index_name = 'idx_notification_user_time'
);
SET @drop_old_user_index_sql := IF(
    @has_old_user_index > 0,
    'ALTER TABLE t_notification_message DROP INDEX idx_notification_user_time',
    'SELECT 1'
);
PREPARE drop_old_user_index FROM @drop_old_user_index_sql;
EXECUTE drop_old_user_index;
DEALLOCATE PREPARE drop_old_user_index;

ALTER TABLE t_notification_message
    ADD KEY idx_notification_user_read_time (user_id, read_status, create_time DESC, id DESC),
    ADD KEY idx_notification_user_event_time (user_id, event_type, create_time DESC, id DESC),
    ADD KEY idx_notification_user_time (user_id, create_time DESC, id DESC);

UPDATE t_notification_user_state
SET last_feed_event_time = COALESCE(last_feed_event_time, '1970-01-01 00:00:00'),
    last_feed_read_time = COALESCE(last_feed_read_time, '1970-01-01 00:00:00');

ALTER TABLE t_notification_user_state
    MODIFY last_feed_event_time DATETIME NOT NULL DEFAULT '1970-01-01 00:00:00' COMMENT '最近一次feed事件时间',
    MODIFY last_feed_read_time DATETIME NOT NULL DEFAULT '1970-01-01 00:00:00' COMMENT '最近一次feed清除时间';
