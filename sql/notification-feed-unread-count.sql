USE game_community;

-- Feed 未读数量与事件幂等迁移。
-- 旧版本只有布尔红点，历史数量无法可靠恢复，迁移后从新事件开始精确累计。
SET @has_feed_unread_count := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 't_notification_user_state'
      AND column_name = 'feed_unread_count'
);
SET @add_feed_unread_count_sql := IF(
    @has_feed_unread_count = 0,
    'ALTER TABLE t_notification_user_state ADD COLUMN feed_unread_count BIGINT NOT NULL DEFAULT 0 COMMENT ''feed未读动态数'' AFTER feed_unread_flag',
    'SELECT 1'
);
PREPARE add_feed_unread_count FROM @add_feed_unread_count_sql;
EXECUTE add_feed_unread_count;
DEALLOCATE PREPARE add_feed_unread_count;

CREATE TABLE IF NOT EXISTS t_notification_feed_event (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id BIGINT NOT NULL COMMENT 'Feed收件用户ID',
    event_id VARCHAR(96) NOT NULL COMMENT '上游Feed事件ID',
    feed_item_id BIGINT DEFAULT NULL COMMENT 'Feed收件箱记录ID',
    occurred_at DATETIME NOT NULL COMMENT '动态发布时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '事件接收时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_notification_feed_event (user_id, event_id),
    KEY idx_notification_feed_event_user_time (user_id, occurred_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Feed未读事件幂等表';
