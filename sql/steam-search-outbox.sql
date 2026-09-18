SET NAMES utf8mb4;

-- Steam 游戏目录提交后可靠投递搜索索引事件。
CREATE TABLE IF NOT EXISTS t_game_search_outbox (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Outbox ID',
    event_key VARCHAR(160) NOT NULL COMMENT '业务事件幂等键',
    app_id BIGINT NOT NULL COMMENT 'Steam AppID',
    action VARCHAR(16) NOT NULL COMMENT 'UPSERT/DELETE',
    payload JSON NOT NULL COMMENT 'GameSearchSyncMessage JSON',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0待发送 1发送中 2已发送 3重试 4死信',
    retry_count INT NOT NULL DEFAULT 0,
    next_retry_time DATETIME DEFAULT NULL,
    lock_token VARCHAR(64) DEFAULT NULL,
    lock_time DATETIME DEFAULT NULL,
    last_error VARCHAR(1000) DEFAULT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_game_search_outbox_event_key (event_key),
    KEY idx_game_search_outbox_pending (status, next_retry_time, id),
    KEY idx_game_search_outbox_lock (status, lock_time, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='游戏搜索索引可靠事件 Outbox';
