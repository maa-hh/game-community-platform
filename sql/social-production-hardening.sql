-- social-service 生产化迁移：保留历史记录，归一化旧数据并补齐约束/索引。
USE game_community;
SET NAMES utf8mb4;

-- 先补齐旧版本尚未存在的收藏表和统计列，再执行字符集转换与数据回填。
CREATE TABLE IF NOT EXISTS t_social_favorite (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '收藏ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    article_id BIGINT NOT NULL COMMENT '文章ID',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
    UNIQUE KEY uk_social_favorite_user_article (user_id, article_id),
    KEY idx_social_favorite_user_time (user_id, create_time, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户收藏文章';

SET @column_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 't_social_article_stats' AND column_name = 'favorite_count'
);
SET @sql := IF(@column_exists = 0,
    "ALTER TABLE t_social_article_stats ADD COLUMN favorite_count BIGINT NOT NULL DEFAULT 0 COMMENT '收藏数' AFTER view_count",
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 't_social_article_stats' AND column_name = 'share_count'
);
SET @sql := IF(@column_exists = 0,
    "ALTER TABLE t_social_article_stats ADD COLUMN share_count BIGINT NOT NULL DEFAULT 0 COMMENT '分享次数' AFTER favorite_count",
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 社交表统一存储引擎、字符集和排序规则。
ALTER TABLE t_social_article_stats CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE t_social_comment CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE t_social_reply CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE t_social_article_like CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE t_social_favorite CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE t_social_comment_like CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE t_social_reply_like CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE t_social_browse_history CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE t_social_feed_item CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE t_social_follow CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE t_social_black CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE t_social_report CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

UPDATE t_social_comment SET avatar = '' WHERE avatar IS NULL;
UPDATE t_social_reply SET avatar = '' WHERE avatar IS NULL;
ALTER TABLE t_social_comment MODIFY avatar VARCHAR(1024) NOT NULL DEFAULT '' COMMENT '用户头像快照';
ALTER TABLE t_social_reply MODIFY avatar VARCHAR(1024) NOT NULL DEFAULT '' COMMENT '用户头像快照';

SET @column_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 't_social_report' AND column_name = 'reported_user_id'
);
SET @sql := IF(@column_exists = 0,
    "ALTER TABLE t_social_report ADD COLUMN reported_user_id BIGINT DEFAULT NULL COMMENT '被举报用户ID' AFTER reporter_id",
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE t_social_report
SET reported_user_id = target_id
WHERE target_type = 4 AND reported_user_id IS NULL;

-- 保留历史重复举报记录，只把重复副本归档为已驳回，再建立数据库唯一约束。
UPDATE t_social_report r
JOIN (
    SELECT reporter_id, target_type, target_id, MIN(id) AS keep_id
    FROM t_social_report
    GROUP BY reporter_id, target_type, target_id
    HAVING COUNT(*) > 1
) d ON d.reporter_id = r.reporter_id
   AND d.target_type = r.target_type
   AND d.target_id = r.target_id
   AND r.id <> d.keep_id
SET r.status = 2,
    r.handle_remark = CONCAT(LEFT(COALESCE(r.handle_remark, ''), 180), '；历史重复举报，主记录：', d.keep_id),
    r.handle_time = COALESCE(r.handle_time, NOW()),
    r.update_time = NOW();

SET @idx_exists := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 't_social_report' AND index_name = 'uk_social_report_dedup'
);
SET @sql := IF(@idx_exists = 0,
    'ALTER TABLE t_social_report ADD UNIQUE KEY uk_social_report_dedup (reporter_id, target_type, target_id)',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 't_social_article_like' AND index_name = 'idx_social_article_like_user_time'
);
SET @sql := IF(@idx_exists = 0,
    'ALTER TABLE t_social_article_like ADD KEY idx_social_article_like_user_time (user_id, create_time, id)',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 't_social_comment_like' AND index_name = 'idx_social_comment_like_user_time'
);
SET @sql := IF(@idx_exists = 0,
    'ALTER TABLE t_social_comment_like ADD KEY idx_social_comment_like_user_time (user_id, create_time, id)',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 't_social_reply_like' AND index_name = 'idx_social_reply_like_user_time'
);
SET @sql := IF(@idx_exists = 0,
    'ALTER TABLE t_social_reply_like ADD KEY idx_social_reply_like_user_time (user_id, create_time, id)',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 't_social_follow' AND index_name = 'idx_social_follow_user_time'
);
SET @sql := IF(@idx_exists = 0,
    'ALTER TABLE t_social_follow ADD KEY idx_social_follow_user_time (user_id, create_time, id)',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 't_social_black' AND index_name = 'idx_social_black_user_time'
);
SET @sql := IF(@idx_exists = 0,
    'ALTER TABLE t_social_black ADD KEY idx_social_black_user_time (user_id, create_time, id)',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

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

-- 重新计算旧统计数据，保留关系表和历史记录，只纠正冗余计数。
UPDATE t_social_article_stats s
LEFT JOIN (SELECT article_id, COUNT(*) c FROM t_social_article_like GROUP BY article_id) l ON l.article_id = s.article_id
LEFT JOIN (SELECT article_id, COUNT(*) c FROM t_social_comment WHERE status = 1 AND deleted = 0 GROUP BY article_id) c ON c.article_id = s.article_id
LEFT JOIN (SELECT article_id, COUNT(*) c FROM t_social_reply WHERE status = 1 AND deleted = 0 GROUP BY article_id) r ON r.article_id = s.article_id
LEFT JOIN (SELECT c.article_id, COUNT(*) n FROM t_social_comment_like x JOIN t_social_comment c ON c.id = x.comment_id WHERE c.status = 1 AND c.deleted = 0 GROUP BY c.article_id) cl ON cl.article_id = s.article_id
LEFT JOIN (SELECT r.article_id, COUNT(*) n FROM t_social_reply_like x JOIN t_social_reply r ON r.id = x.reply_id WHERE r.status = 1 AND r.deleted = 0 GROUP BY r.article_id) rl ON rl.article_id = s.article_id
LEFT JOIN (SELECT article_id, COUNT(*) c FROM t_social_favorite GROUP BY article_id) f ON f.article_id = s.article_id
LEFT JOIN (SELECT article_id, COUNT(*) c FROM t_social_browse_history GROUP BY article_id) v ON v.article_id = s.article_id
SET s.like_count = COALESCE(l.c, 0),
    s.comment_count = COALESCE(c.c, 0),
    s.reply_count = COALESCE(r.c, 0),
    s.comment_like_count = COALESCE(cl.n, 0),
    s.reply_like_count = COALESCE(rl.n, 0),
    s.favorite_count = COALESCE(f.c, 0),
    s.view_count = COALESCE(v.c, 0),
    s.update_time = NOW();
