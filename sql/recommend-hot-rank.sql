-- 热榜历史快照
CREATE TABLE IF NOT EXISTS t_hot_rank_snapshot (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    board_type VARCHAR(16) NOT NULL COMMENT 'total/weekly/daily',
    period_key VARCHAR(16) NOT NULL COMMENT 'yyyy-MM-dd 或 yyyy-Www',
    category_scope VARCHAR(32) NOT NULL COMMENT 'all 或 cat:{id}',
    article_id BIGINT NOT NULL,
    rank_no INT NOT NULL,
    hot_score DOUBLE NOT NULL,
    snapshot_time DATETIME NOT NULL,
    KEY idx_board_period_scope_rank (board_type, period_key, category_scope, rank_no),
    KEY idx_board_period_article (board_type, period_key, article_id),
    UNIQUE KEY uk_board_period_scope_rank (board_type, period_key, category_scope, rank_no),
    UNIQUE KEY uk_board_period_scope_article (board_type, period_key, category_scope, article_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
