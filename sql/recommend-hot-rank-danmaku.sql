-- 弹幕纳入热榜热度：兼容已存在的行为事件表，并幂等回填历史可见弹幕。
SET NAMES utf8mb4;

SET @column_exists := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 't_article_behavior_event'
      AND column_name = 'danmaku_delta'
);
SET @sql := IF(
    @column_exists = 0,
    'ALTER TABLE t_article_behavior_event ADD COLUMN danmaku_delta BIGINT NOT NULL DEFAULT 0 COMMENT ''弹幕增量，权重与评论相同'' AFTER comment_delta',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

INSERT IGNORE INTO t_article_behavior_event (
    event_id, article_id, like_delta, comment_delta, danmaku_delta, view_delta,
    favorite_delta, share_delta, comment_like_delta, reply_like_delta,
    score_delta, event_time, event_time_ms
)
SELECT
    CONCAT('backfill-danmaku-', d.id), a.id,
    0, 0, 1, 0,
    0, 0, 0, 0,
    5.0,
    COALESCE(d.create_time, NOW()),
    UNIX_TIMESTAMP(COALESCE(d.create_time, NOW())) * 1000
FROM t_danmaku_message d
INNER JOIN t_article a ON a.public_id = d.video_public_id
    AND a.post_type = 3 AND a.status = 1 AND a.deleted = 0
WHERE d.status = 1;
