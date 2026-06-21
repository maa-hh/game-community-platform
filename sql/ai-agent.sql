CREATE TABLE IF NOT EXISTS t_ai_knowledge_document (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(128) NOT NULL,
    source_type TINYINT NOT NULL,
    source_name VARCHAR(255) DEFAULT NULL,
    content_hash VARCHAR(64) NOT NULL,
    status TINYINT NOT NULL DEFAULT 1,
    index_status TINYINT NOT NULL DEFAULT 0,
    segment_count INT NOT NULL DEFAULT 0,
    created_by BIGINT DEFAULT NULL,
    updated_by BIGINT DEFAULT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_ai_knowledge_content_hash (content_hash),
    KEY idx_ai_knowledge_status (status),
    KEY idx_ai_knowledge_index_status (index_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 知识文档表';
