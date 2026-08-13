-- 商城一致性增强：支付 Outbox。数据库支付事务成功后，Kafka 事件至少可投递一次。
USE game_community;
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS t_shop_order_paid_outbox (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    event_id VARCHAR(64) NOT NULL,
    order_no VARCHAR(40) NOT NULL,
    topic VARCHAR(128) NOT NULL,
    message_key VARCHAR(128) NOT NULL,
    payload JSON NOT NULL,
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0待发送 1发送中 2已发送 3重试 4死信',
    retry_count INT NOT NULL DEFAULT 0,
    next_retry_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    lock_token VARCHAR(64) NOT NULL DEFAULT '',
    lock_time DATETIME NOT NULL DEFAULT '1970-01-01 00:00:00',
    last_error VARCHAR(1000) NOT NULL DEFAULT '',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_shop_paid_outbox_event (event_id),
    UNIQUE KEY uk_shop_paid_outbox_order (order_no),
    KEY idx_shop_paid_outbox_pending (status, next_retry_time, id),
    KEY idx_shop_paid_outbox_lock (status, lock_time, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商城支付 Kafka 事务 Outbox';
