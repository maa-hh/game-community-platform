SET NAMES utf8mb4;

-- 生产加固迁移 v2：兼容已执行旧迁移的新库和已有库，索引创建保持幂等。
UPDATE t_suggest_term
SET source_article_id = COALESCE(source_article_id, 0),
    last_triggered_at = COALESCE(last_triggered_at, '1970-01-01 00:00:00');

ALTER TABLE t_suggest_term
    MODIFY source_article_id BIGINT NOT NULL DEFAULT 0 COMMENT '来源帖子ID，0表示非帖子来源',
    MODIFY last_triggered_at DATETIME NOT NULL DEFAULT '1970-01-01 00:00:00' COMMENT '最近触发时间，未触发使用纪元时间';

CREATE TABLE IF NOT EXISTS t_suggest_term_source (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '来源关系ID',
    term_id BIGINT NOT NULL COMMENT '建议词ID',
    source_type VARCHAR(16) NOT NULL COMMENT 'ARTICLE/AI/GAME/UPLOAD',
    source_article_id BIGINT NOT NULL DEFAULT 0 COMMENT '来源帖子ID，0表示非帖子来源',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '建立关系时间',
    UNIQUE KEY uk_suggest_term_source (term_id, source_type, source_article_id),
    KEY idx_suggest_source_article_type (source_article_id, source_type, term_id),
    KEY idx_suggest_term_source_term (term_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='建议词来源关系';

INSERT IGNORE INTO t_suggest_term_source (term_id, source_type, source_article_id)
SELECT id, source_type, source_article_id
FROM t_suggest_term;

SET @suggest_index_exists := (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 't_suggest_term'
      AND index_name = 'idx_suggest_status_weight_id'
);
SET @suggest_index_sql := IF(
    @suggest_index_exists = 0,
    'ALTER TABLE t_suggest_term ADD KEY idx_suggest_status_weight_id (status, weight, id)',
    'SELECT 1'
);
PREPARE suggest_index_stmt FROM @suggest_index_sql;
EXECUTE suggest_index_stmt;
DEALLOCATE PREPARE suggest_index_stmt;
