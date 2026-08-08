-- 用户通知 Outbox 补齐迁移
-- user.sql 已在既有环境执行过，但通知可靠投递表是在后续版本加入的；仅补齐缺失表。
CREATE TABLE IF NOT EXISTS t_user_notification_outbox (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_key VARCHAR(96) NOT NULL,
    payload JSON NOT NULL,
    status TINYINT NOT NULL DEFAULT 0,
    retry_count INT NOT NULL DEFAULT 0,
    next_retry_time DATETIME DEFAULT NULL,
    lock_token VARCHAR(64) DEFAULT NULL,
    lock_time DATETIME DEFAULT NULL,
    last_error VARCHAR(1000) DEFAULT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_notification_outbox_event (event_key),
    KEY idx_user_notification_outbox_pending (status, next_retry_time, id),
    KEY idx_user_notification_outbox_lock (status, lock_time, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户通知可靠事件 Outbox';
