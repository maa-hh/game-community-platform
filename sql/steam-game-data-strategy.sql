-- Steam 游戏数据分层与更新状态（最终结构，一次性迁移）
USE game_community;
SET NAMES utf8mb4;

ALTER TABLE t_game_catalog
    ADD COLUMN static_synced_at DATETIME NULL COMMENT '静态数据最近成功更新时间',
    ADD COLUMN metrics_synced_at DATETIME NULL COMMENT 'Steam 评分数据最近成功更新时间',
    ADD COLUMN price_synced_at DATETIME NULL COMMENT '价格数据最近成功更新时间',
    ADD COLUMN rich_synced_at DATETIME NULL COMMENT 'Mongo 富详情最近成功更新时间',
    ADD COLUMN last_refresh_attempt_at DATETIME NULL COMMENT '最近一次刷新尝试时间',
    ADD COLUMN next_refresh_at DATETIME NULL COMMENT '下一次允许刷新时间',
    ADD COLUMN refresh_status VARCHAR(16) NOT NULL DEFAULT 'READY' COMMENT 'READY/RUNNING/FAILED',
    ADD COLUMN detail_ready TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已完成完整详情初始化',
    ADD KEY idx_game_refresh (status, next_refresh_at, app_id),
    ADD KEY idx_game_metrics (status, metrics_synced_at, steam_review_count),
    ADD KEY idx_game_price (status, price_synced_at, price_discount);

UPDATE t_game_catalog
SET static_synced_at = steam_synced_at,
    metrics_synced_at = CASE WHEN steam_review_count IS NOT NULL THEN steam_synced_at ELSE NULL END,
    price_synced_at = CASE WHEN price_final IS NOT NULL OR steam_is_free = 1 THEN steam_synced_at ELSE NULL END,
    rich_synced_at = steam_synced_at,
    last_refresh_attempt_at = steam_synced_at,
    next_refresh_at = DATE_ADD(steam_synced_at, INTERVAL 7 DAY),
    refresh_status = 'READY',
    detail_ready = 1
WHERE steam_synced_at IS NOT NULL;

ALTER TABLE t_game_chart_snapshot
    ADD COLUMN snapshot_id VARCHAR(32) NOT NULL COMMENT '一次完整同步的版本号',
    ADD COLUMN is_current TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否当前生效版本',
    ADD KEY idx_chart_current (board_type, is_current, rank_no);

UPDATE t_game_chart_snapshot
SET snapshot_id = MD5(CONCAT(board_type, ':', period_key));

UPDATE t_game_chart_snapshot current_row
JOIN (
    SELECT board_type, MAX(snapshot_time) AS max_snapshot_time
    FROM t_game_chart_snapshot
    GROUP BY board_type
) latest ON latest.board_type = current_row.board_type
       AND latest.max_snapshot_time = current_row.snapshot_time
SET current_row.is_current = 1;

-- 富详情已迁移至 MongoDB steam_game_detail，MySQL 只保留列表、指标、价格和刷新状态。
ALTER TABLE t_game_catalog
    DROP COLUMN steam_short_desc,
    DROP COLUMN steam_about_html,
    DROP COLUMN steam_screenshots,
    DROP COLUMN steam_movies,
    DROP COLUMN steam_categories,
    DROP COLUMN metacritic_score,
    DROP COLUMN metacritic_url,
    DROP COLUMN achievement_total,
    DROP COLUMN achievement_highlights,
    DROP COLUMN pc_requirements_min,
    DROP COLUMN pc_requirements_rec;
