SET NAMES utf8mb4;

-- 搜索前缀建议词主数据（MySQL 管生命周期，ES 管检索）
CREATE TABLE IF NOT EXISTS t_suggest_term (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '建议词ID',
    term VARCHAR(128) NOT NULL COMMENT '规范化后的建议词',
    source_type VARCHAR(16) NOT NULL COMMENT 'ARTICLE/AI/GAME/UPLOAD',
    source_article_id BIGINT NOT NULL DEFAULT 0 COMMENT '来源业务ID，文章/游戏使用对应ID，0表示无实体来源',
    weight INT NOT NULL DEFAULT 1 COMMENT '排序权重，越大越靠前',
    pinned TINYINT NOT NULL DEFAULT 0 COMMENT '运营置顶，不参与自动清理',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED/EXPIRED',
    trigger_count BIGINT NOT NULL DEFAULT 0 COMMENT '被选中或搜索触发次数',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '入库时间',
    last_triggered_at DATETIME NOT NULL DEFAULT '1970-01-01 00:00:00' COMMENT '最近触发时间，未触发使用纪元时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_suggest_term (term),
    KEY idx_suggest_source_article (source_article_id, status),
    KEY idx_suggest_cleanup (source_type, status, pinned, last_triggered_at, created_at),
    KEY idx_suggest_status_weight_id (status, weight, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='搜索前缀建议词';

CREATE TABLE IF NOT EXISTS t_suggest_term_source (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '来源关系ID',
    term_id BIGINT NOT NULL COMMENT '建议词ID',
    source_type VARCHAR(16) NOT NULL COMMENT 'ARTICLE/AI/GAME/UPLOAD',
    source_article_id BIGINT NOT NULL DEFAULT 0 COMMENT '来源业务ID，文章/游戏使用对应ID，0表示无实体来源',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '建立关系时间',
    UNIQUE KEY uk_suggest_term_source (term_id, source_type, source_article_id),
    KEY idx_suggest_source_article_type (source_article_id, source_type, term_id),
    KEY idx_suggest_term_source_term (term_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='建议词来源关系';
