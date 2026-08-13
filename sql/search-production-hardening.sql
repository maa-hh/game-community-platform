SET NAMES utf8mb4;

-- 搜索服务生产加固迁移：先回填，再收紧 NOT NULL，最后建立多来源关系。
-- 在已存在的生产库执行一次；新库直接执行 search-suggest-term.sql 即可。
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

ALTER TABLE t_suggest_term
    ADD KEY idx_suggest_status_weight_id (status, weight, id);
