-- 帖子公开 ID 迁移，可重复执行。
USE game_community;
SET NAMES utf8mb4;

SET @column_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 't_article' AND column_name = 'public_id'
);
SET @sql := IF(@column_exists = 0,
    'ALTER TABLE t_article ADD COLUMN public_id VARCHAR(32) DEFAULT NULL COMMENT ''对外公开帖子ID'' AFTER id',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 为历史帖子生成不可枚举的公开 ID。
UPDATE t_article
SET public_id = REPLACE(UUID(), '-', '')
WHERE public_id IS NULL OR public_id = '';

ALTER TABLE t_article
    MODIFY COLUMN public_id VARCHAR(32) NOT NULL COMMENT '对外公开帖子ID';

SET @index_exists := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 't_article' AND index_name = 'uk_article_public_id'
);
SET @sql := IF(@index_exists = 0,
    'ALTER TABLE t_article ADD UNIQUE KEY uk_article_public_id (public_id)',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ref_article_id 原来存内部自增主键；迁移为原帖 public_id。
ALTER TABLE t_article
    MODIFY COLUMN ref_article_id VARCHAR(32) DEFAULT NULL COMMENT '转发引用的原帖public_id';

UPDATE t_article repost
JOIN t_article original ON CAST(repost.ref_article_id AS UNSIGNED) = original.id
SET repost.ref_article_id = original.public_id
WHERE repost.post_type = 4
  AND repost.ref_article_id REGEXP '^[0-9]+$';

-- 迁移后检查：任何返回行都必须在切流前处理。
SELECT 'public_id_null' AS check_name, COUNT(*) AS invalid_count
FROM t_article WHERE public_id IS NULL OR public_id = '';
SELECT 'repost_ref_invalid' AS check_name, COUNT(*) AS invalid_count
FROM t_article repost
LEFT JOIN t_article original ON original.public_id = repost.ref_article_id
WHERE repost.post_type = 4
  AND repost.ref_article_id IS NOT NULL
  AND original.id IS NULL;
