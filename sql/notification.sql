USE game_community;

CREATE TABLE IF NOT EXISTS t_notification_message (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    event_id VARCHAR(96) NOT NULL COMMENT '上游事件唯一ID',
    aggregate_key VARCHAR(255) NOT NULL COMMENT '聚合维度键',
    user_id BIGINT NOT NULL COMMENT '通知接收人',
    event_type TINYINT NOT NULL COMMENT '通知事件类型',
    actor_user_id BIGINT DEFAULT NULL COMMENT '触发人用户ID',
    actor_username VARCHAR(64) NOT NULL DEFAULT '' COMMENT '触发人用户名',
    actor_avatar VARCHAR(512) NOT NULL DEFAULT '' COMMENT '触发人头像',
    article_id BIGINT DEFAULT NULL COMMENT '文章ID',
    comment_id BIGINT DEFAULT NULL COMMENT '评论ID',
    reply_id BIGINT DEFAULT NULL COMMENT '回复ID',
    report_id BIGINT DEFAULT NULL COMMENT '举报ID',
    target_user_id BIGINT DEFAULT NULL COMMENT '跳转目标用户ID',
    preview_text VARCHAR(255) NOT NULL DEFAULT '' COMMENT '通知简要文案',
    result_text TEXT NOT NULL COMMENT '结果文案或聚合参与者快照',
    route_type TINYINT NOT NULL DEFAULT 0 COMMENT '跳转意图',
    read_status TINYINT NOT NULL DEFAULT 0 COMMENT '已读状态 0未读 1已读',
    read_time DATETIME NOT NULL DEFAULT '1970-01-01 00:00:00' COMMENT '已读时间，未读为纪元时间',
    occurred_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '业务事件发生时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_notification_message_event (user_id, event_id),
    KEY idx_notification_user_read_time (user_id, read_status, create_time DESC, id DESC),
    KEY idx_notification_user_event_time (user_id, event_type, create_time DESC, id DESC),
    KEY idx_notification_user_time (user_id, create_time DESC, id DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='站内通知消息表';

CREATE TABLE IF NOT EXISTS t_notification_user_state (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    unread_notification_count BIGINT NOT NULL DEFAULT 0 COMMENT '普通通知未读数',
    feed_unread_flag TINYINT NOT NULL DEFAULT 0 COMMENT 'feed未读红点 0否 1是',
    last_feed_event_time DATETIME NOT NULL DEFAULT '1970-01-01 00:00:00' COMMENT '最近一次feed事件时间',
    last_feed_read_time DATETIME NOT NULL DEFAULT '1970-01-01 00:00:00' COMMENT '最近一次feed清除时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_notification_state_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通知用户状态表';
