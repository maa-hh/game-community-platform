-- 弹幕可靠事件的落库表。实时发送不直接写该表，由 danmaku-service 消费 Kafka 异步幂等落库。
CREATE TABLE IF NOT EXISTS t_danmaku_message (
    id BIGINT PRIMARY KEY COMMENT '由 Redis 原子序列生成的弹幕 ID',
    event_id VARCHAR(64) NOT NULL COMMENT 'Kafka 事件 ID',
    client_message_id VARCHAR(64) NOT NULL COMMENT '客户端幂等 ID',
    video_public_id VARCHAR(64) NOT NULL COMMENT '视频帖公开 ID',
    video_time_ms BIGINT NOT NULL COMMENT '用户发送时的视频时间',
    display_time_ms BIGINT NOT NULL COMMENT '展示时间，支持服务端后续调度',
    seq BIGINT NOT NULL COMMENT '视频内顺序号',
    user_id BIGINT NOT NULL,
    account_id BIGINT NOT NULL COMMENT '对外账号ID',
    username_snapshot VARCHAR(128) DEFAULT NULL,
    avatar_snapshot VARCHAR(512) DEFAULT NULL,
    content VARCHAR(200) NOT NULL,
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1可见 2隐藏',
    version INT NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_danmaku_event_id (event_id),
    UNIQUE KEY uk_danmaku_client_message (user_id, video_public_id, client_message_id),
    KEY idx_danmaku_video_time (video_public_id, display_time_ms, seq),
    KEY idx_danmaku_user_time (user_id, create_time),
    KEY idx_danmaku_account_time (account_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='视频弹幕历史与审核事实表';
