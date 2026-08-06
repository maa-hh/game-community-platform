-- 文章多分类：JSON 作为兼容快照，t_article_category 作为查询规范化关系表。
-- category_id 保留为主分类（列表首项），兼容旧索引与查询

ALTER TABLE t_article
    ADD COLUMN category_ids JSON NULL COMMENT '分类ID列表（JSON数组，首项与 category_id 一致）' AFTER category_id;

UPDATE t_article
SET category_ids = JSON_ARRAY(category_id)
WHERE category_ids IS NULL
  AND category_id IS NOT NULL
  AND category_id > 0;

CREATE TABLE IF NOT EXISTS t_article_category (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    article_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_article_category (article_id, category_id),
    KEY idx_category_article (category_id, article_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文章分类关系';

INSERT IGNORE INTO t_article_category (article_id, category_id)
SELECT a.id, jt.category_id
FROM t_article a
JOIN JSON_TABLE(a.category_ids, '$[*]' COLUMNS (category_id BIGINT PATH '$')) jt
WHERE a.category_ids IS NOT NULL;
