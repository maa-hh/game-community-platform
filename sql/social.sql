CREATE TABLE IF NOT EXISTS t_social_article_stats (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '统计ID',
    article_id BIGINT NOT NULL COMMENT '文章ID',
    like_count BIGINT NOT NULL DEFAULT 0 COMMENT '点赞数',
    comment_count BIGINT NOT NULL DEFAULT 0 COMMENT '评论数',
    comment_like_count BIGINT NOT NULL DEFAULT 0 COMMENT '评论点赞数',
    reply_count BIGINT NOT NULL DEFAULT 0 COMMENT '回复数',
    reply_like_count BIGINT NOT NULL DEFAULT 0 COMMENT '回复点赞数',
    view_count BIGINT NOT NULL DEFAULT 0 COMMENT '浏览数',
    favorite_count BIGINT NOT NULL DEFAULT 0 COMMENT '收藏数',
    share_count BIGINT NOT NULL DEFAULT 0 COMMENT '分享次数',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_social_article_stats_article (article_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文章社交统计表';

CREATE TABLE IF NOT EXISTS t_social_comment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '评论ID',
    article_id BIGINT NOT NULL COMMENT '文章ID',
    user_id BIGINT NOT NULL COMMENT '评论用户ID',
    username VARCHAR(64) NOT NULL COMMENT '用户昵称快照',
    avatar VARCHAR(1024) NOT NULL DEFAULT '' COMMENT '用户头像快照',
    like_count BIGINT NOT NULL DEFAULT 0 COMMENT '点赞数',
    reply_count BIGINT NOT NULL DEFAULT 0 COMMENT '回复数',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 1-正常, 2-隐藏, 3-已删除',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    KEY idx_social_comment_article_time (article_id, status, create_time, id),
    KEY idx_social_comment_user_time (user_id, create_time, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评论元数据表，正文存 MongoDB';

CREATE TABLE IF NOT EXISTS t_social_reply (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '回复ID',
    comment_id BIGINT NOT NULL COMMENT '评论ID',
    article_id BIGINT NOT NULL COMMENT '文章ID',
    user_id BIGINT NOT NULL COMMENT '回复用户ID',
    username VARCHAR(64) NOT NULL COMMENT '用户昵称快照',
    avatar VARCHAR(1024) NOT NULL DEFAULT '' COMMENT '用户头像快照',
    reply_to_user_id BIGINT DEFAULT NULL COMMENT '被回复用户ID',
    reply_to_username VARCHAR(64) DEFAULT NULL COMMENT '被回复用户昵称快照',
    content VARCHAR(1000) NOT NULL COMMENT '回复内容',
    like_count BIGINT NOT NULL DEFAULT 0 COMMENT '点赞数',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 1-正常, 2-隐藏, 3-已删除',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    KEY idx_social_reply_comment_time (comment_id, status, create_time, id),
    KEY idx_social_reply_user_time (user_id, create_time, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评论回复表';

CREATE TABLE IF NOT EXISTS t_social_article_like (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '点赞ID',
    article_id BIGINT NOT NULL COMMENT '文章ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_social_article_like_user_article (user_id, article_id),
    KEY idx_social_article_like_article (article_id, create_time, id),
    KEY idx_social_article_like_user_time (user_id, create_time, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文章点赞表';

CREATE TABLE IF NOT EXISTS t_social_favorite (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '收藏ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    article_id BIGINT NOT NULL COMMENT '文章ID',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
    UNIQUE KEY uk_social_favorite_user_article (user_id, article_id),
    KEY idx_social_favorite_user_time (user_id, create_time, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户收藏文章';

CREATE TABLE IF NOT EXISTS t_social_comment_like (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '点赞ID',
    comment_id BIGINT NOT NULL COMMENT '评论ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_social_comment_like_user_comment (user_id, comment_id),
    KEY idx_social_comment_like_comment (comment_id, create_time, id),
    KEY idx_social_comment_like_user_time (user_id, create_time, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评论点赞表';

CREATE TABLE IF NOT EXISTS t_social_reply_like (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '点赞ID',
    reply_id BIGINT NOT NULL COMMENT '回复ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_social_reply_like_user_reply (user_id, reply_id),
    KEY idx_social_reply_like_reply (reply_id, create_time, id),
    KEY idx_social_reply_like_user_time (user_id, create_time, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='回复点赞表';

CREATE TABLE IF NOT EXISTS t_social_browse_history (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '浏览记录ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    article_id BIGINT NOT NULL COMMENT '文章ID',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '首次浏览时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最近浏览时间',
    UNIQUE KEY uk_social_browse_user_article (user_id, article_id),
    KEY idx_social_browse_user_time (user_id, update_time, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文章浏览历史表';

CREATE TABLE IF NOT EXISTS t_social_feed_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Feed记录ID',
    user_id BIGINT NOT NULL COMMENT '收件用户ID',
    author_id BIGINT NOT NULL COMMENT '作者用户ID',
    article_id BIGINT NOT NULL COMMENT '文章ID',
    published_time DATETIME NOT NULL COMMENT '文章发布时间',
    source_type TINYINT NOT NULL DEFAULT 1 COMMENT '来源: 1-发布推送, 2-关注补偿',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '入信箱时间',
    UNIQUE KEY uk_social_feed_user_article (user_id, article_id),
    KEY idx_social_feed_user_time (user_id, published_time, article_id),
    KEY idx_social_feed_author_time (author_id, published_time, article_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户Feed信箱表';

CREATE TABLE IF NOT EXISTS t_social_follow (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '关注ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    follow_user_id BIGINT NOT NULL COMMENT '被关注用户ID',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_social_follow_pair (user_id, follow_user_id),
    KEY idx_social_follow_target (follow_user_id, create_time, id),
    KEY idx_social_follow_user_time (user_id, create_time, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户关注关系表';

CREATE TABLE IF NOT EXISTS t_social_black (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '拉黑ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    black_user_id BIGINT NOT NULL COMMENT '被拉黑用户ID',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_social_black_pair (user_id, black_user_id),
    KEY idx_social_black_target (black_user_id, create_time, id),
    KEY idx_social_black_user_time (user_id, create_time, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户黑名单表';

CREATE TABLE IF NOT EXISTS t_social_report (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '举报ID',
    target_type TINYINT NOT NULL COMMENT '目标类型: 1-文章, 2-评论, 3-回复, 4-用户',
    target_id BIGINT NOT NULL COMMENT '目标ID',
    reporter_id BIGINT NOT NULL COMMENT '举报用户ID',
    reported_user_id BIGINT DEFAULT NULL COMMENT '被举报用户ID',
    reason VARCHAR(255) NOT NULL COMMENT '举报原因',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '状态: 0-待处理, 1-已采纳, 2-已驳回',
    handler_id BIGINT DEFAULT NULL COMMENT '处理人ID',
    handle_remark VARCHAR(255) DEFAULT NULL COMMENT '处理说明',
    handle_time DATETIME DEFAULT NULL COMMENT '处理时间',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY idx_social_report_status_time (status, create_time, id),
    KEY idx_social_report_target (target_type, target_id),
    KEY idx_social_report_reporter (reporter_id, create_time, id),
    UNIQUE KEY uk_social_report_dedup (reporter_id, target_type, target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='社交举报表';

CREATE TABLE IF NOT EXISTS t_social_outbox (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    event_key VARCHAR(160) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    topic VARCHAR(128) NOT NULL,
    message_key VARCHAR(128) NOT NULL,
    payload JSON NOT NULL,
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0待发送 1发送中 2已发送 3重试 4死信',
    retry_count INT NOT NULL DEFAULT 0,
    next_retry_time DATETIME DEFAULT NULL,
    lock_token VARCHAR(64) DEFAULT NULL,
    lock_time DATETIME DEFAULT NULL,
    last_error VARCHAR(1000) DEFAULT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_social_outbox_event_key (event_key),
    KEY idx_social_outbox_pending (status, next_retry_time, id),
    KEY idx_social_outbox_lock (status, lock_time, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='社交可靠事件 Outbox';

/*
MongoDB 集合: social_comment_content
{
  commentId: NumberLong,  // 唯一索引
  articleId: NumberLong,
  userId: NumberLong,
  content: String,
  createTime: ISODate,
  updateTime: ISODate
}
建议索引:
db.social_comment_content.createIndex({ commentId: 1 }, { unique: true })
db.social_comment_content.createIndex({ articleId: 1, createTime: -1 })
*/
