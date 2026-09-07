-- Feed 信箱按帖子类型独立限容。
-- 历史信箱记录从文章表回填 post_type，之后每个类型单独受 FEED_CAPACITY 约束。
-- 该文件用于已执行 social.sql 的数据库，禁止把结构变更回填到 social.sql。

USE game_community;
SET NAMES utf8mb4;

SET @has_post_type := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 't_social_feed_item'
      AND column_name = 'post_type'
);
SET @add_post_type_sql := IF(
    @has_post_type = 0,
    'ALTER TABLE t_social_feed_item ADD COLUMN post_type TINYINT NULL COMMENT ''帖子类型: 1-图文, 2-文章, 3-视频, 4-转发'' AFTER article_id',
    'SELECT 1'
);
PREPARE add_post_type FROM @add_post_type_sql;
EXECUTE add_post_type;
DEALLOCATE PREPARE add_post_type;

UPDATE t_social_feed_item feed
JOIN t_article article ON article.id = feed.article_id
SET feed.post_type = article.post_type
WHERE feed.post_type IS NULL;

UPDATE t_social_feed_item
SET post_type = 2
WHERE post_type IS NULL;

SET @needs_post_type_normalization := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 't_social_feed_item'
      AND column_name = 'post_type'
      AND (
          column_type <> 'tinyint'
          OR is_nullable <> 'NO'
          OR COALESCE(column_default, '') <> '2'
      )
);
SET @normalize_post_type_sql := IF(
    @needs_post_type_normalization > 0,
    'ALTER TABLE t_social_feed_item MODIFY COLUMN post_type TINYINT NOT NULL DEFAULT 2 COMMENT ''帖子类型: 1-图文, 2-文章, 3-视频, 4-转发''',
    'SELECT 1'
);
PREPARE normalize_post_type FROM @normalize_post_type_sql;
EXECUTE normalize_post_type;
DEALLOCATE PREPARE normalize_post_type;

SET @has_type_time_index := (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 't_social_feed_item'
      AND index_name = 'idx_social_feed_user_type_time'
);
SET @add_type_time_index_sql := IF(
    @has_type_time_index = 0,
    'ALTER TABLE t_social_feed_item ADD KEY idx_social_feed_user_type_time (user_id, post_type, published_time, article_id)',
    'SELECT 1'
);
PREPARE add_type_time_index FROM @add_type_time_index_sql;
EXECUTE add_type_time_index;
DEALLOCATE PREPARE add_type_time_index;
