-- Steam 国区游戏榜单快照（每日同步）
USE game_community;
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS t_game_chart_snapshot (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    board_type VARCHAR(16) NOT NULL COMMENT 'hot/new/free/discount',
    period_key VARCHAR(16) NOT NULL COMMENT 'yyyy-MM-dd',
    app_id BIGINT NOT NULL,
    rank_no INT NOT NULL,
    snapshot_time DATETIME NOT NULL,
    snapshot_id VARCHAR(32) NOT NULL COMMENT '一次完整同步的版本号',
    is_current TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否当前生效版本',
    UNIQUE KEY uk_board_period_app (board_type, period_key, app_id),
    KEY idx_board_period_rank (board_type, period_key, rank_no),
    KEY idx_chart_current (board_type, is_current, rank_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
