SET NAMES utf8mb4;

USE game_community;

/*
 * 通知服务增量加固：只在索引缺失时补齐，避免重跑时重复建索引。
 * 历史 notification*.sql 已由迁移同步器锁定，本脚本承接后续可重入修复。
 */
SET @has_notification_event_time_index := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 't_notification_message'
      AND index_name = 'idx_notification_user_event_time'
);
SET @add_notification_event_time_index_sql := IF(
    @has_notification_event_time_index = 0,
    'ALTER TABLE t_notification_message ADD KEY idx_notification_user_event_time (user_id, event_type, create_time DESC, id DESC)',
    'SELECT 1'
);
PREPARE add_notification_event_time_index FROM @add_notification_event_time_index_sql;
EXECUTE add_notification_event_time_index;
DEALLOCATE PREPARE add_notification_event_time_index;
