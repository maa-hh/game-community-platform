USE game_community;

/*
 * 通知服务生产加固迁移：幂等、聚合、事件时间和稳定分页索引。
 * 适用于已经执行过旧版 notification.sql 的数据库；这是一次性版本迁移，
 * 请由迁移工具记录版本，不要重复执行。
 */
ALTER TABLE t_notification_message
    ADD COLUMN event_id VARCHAR(96) NOT NULL DEFAULT '' COMMENT '上游事件唯一ID' AFTER id,
    ADD COLUMN aggregate_key VARCHAR(255) NOT NULL DEFAULT '' COMMENT '聚合维度键' AFTER event_id,
    ADD COLUMN occurred_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '业务事件发生时间' AFTER read_time;

UPDATE t_notification_message
SET event_id = CONCAT('legacy:', id),
    aggregate_key = CONCAT('legacy:', id),
    occurred_at = COALESCE(occurred_at, create_time),
    read_time = COALESCE(read_time, '1970-01-01 00:00:00')
WHERE event_id = '' OR aggregate_key = '';

UPDATE t_notification_message
SET result_text = ''
WHERE result_text IS NULL;

ALTER TABLE t_notification_message
    MODIFY COLUMN result_text TEXT NOT NULL,
    MODIFY COLUMN read_time DATETIME NOT NULL DEFAULT '1970-01-01 00:00:00';

ALTER TABLE t_notification_message
    ADD UNIQUE KEY uk_notification_message_event (user_id, event_id),
    ADD KEY idx_notification_user_read_time (user_id, read_status, create_time DESC, id DESC),
    ADD KEY idx_notification_user_event_time (user_id, event_type, create_time DESC, id DESC),
    ADD KEY idx_notification_user_time (user_id, create_time DESC, id DESC);

ALTER TABLE t_notification_user_state
    MODIFY COLUMN last_feed_event_time DATETIME NOT NULL DEFAULT '1970-01-01 00:00:00',
    MODIFY COLUMN last_feed_read_time DATETIME NOT NULL DEFAULT '1970-01-01 00:00:00';

UPDATE t_notification_user_state
SET last_feed_event_time = COALESCE(last_feed_event_time, '1970-01-01 00:00:00'),
    last_feed_read_time = COALESCE(last_feed_read_time, '1970-01-01 00:00:00');
