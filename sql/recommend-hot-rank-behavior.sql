-- 热榜行为事件明细（带时间戳，可回放聚合任意日/周榜）
CREATE TABLE IF NOT EXISTS t_article_behavior_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id VARCHAR(64) NOT NULL COMMENT '全局幂等事件ID',
    article_id BIGINT NOT NULL,
    like_delta BIGINT NOT NULL DEFAULT 0,
    comment_delta BIGINT NOT NULL DEFAULT 0,
    view_delta BIGINT NOT NULL DEFAULT 0,
    favorite_delta BIGINT NOT NULL DEFAULT 0,
    share_delta BIGINT NOT NULL DEFAULT 0,
    comment_like_delta BIGINT NOT NULL DEFAULT 0,
    reply_like_delta BIGINT NOT NULL DEFAULT 0,
    score_delta DOUBLE NOT NULL,
    event_time DATETIME(3) NOT NULL COMMENT '行为发生时刻（上海时区）',
    event_time_ms BIGINT NOT NULL,
    UNIQUE KEY uk_behavior_event_id (event_id),
    KEY idx_event_time (event_time),
    KEY idx_article_event_time (article_id, event_time),
    KEY idx_event_time_article (event_time, article_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
