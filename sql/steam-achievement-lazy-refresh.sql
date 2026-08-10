USE game_community;
SET NAMES utf8mb4;

ALTER TABLE t_user_steam_game
    ADD COLUMN achievement_synced_at DATETIME NULL COMMENT '玩家成就最近成功同步时间',
    ADD COLUMN achievement_last_attempt_at DATETIME NULL COMMENT '玩家成就最近一次尝试时间',
    ADD COLUMN achievement_next_refresh_at DATETIME NULL COMMENT '下一次允许懒更新时间',
    ADD COLUMN achievement_refresh_status VARCHAR(32) NULL COMMENT 'READY/LOADING/FAILED/NOT_AVAILABLE',
    ADD COLUMN achievement_sync_id VARCHAR(32) NULL COMMENT '玩家成就快照版本',
    ADD COLUMN achievement_fail_count INT NOT NULL DEFAULT 0 COMMENT '连续失败次数';

CREATE TABLE IF NOT EXISTS t_game_achievement (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    app_id BIGINT NOT NULL,
    api_name VARCHAR(200) NOT NULL,
    name VARCHAR(500) NULL,
    description VARCHAR(2000) NULL,
    icon_url VARCHAR(1000) NULL,
    global_percent DECIMAL(6,3) NULL COMMENT 'Steam 全球获取率 0-100',
    synced_at DATETIME NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_game_achievement (app_id, api_name),
    KEY idx_game_achievement_sync (app_id, synced_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Steam 公共成就定义';

CREATE TABLE IF NOT EXISTS t_user_game_achievement (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    app_id BIGINT NOT NULL,
    api_name VARCHAR(200) NOT NULL,
    unlocked TINYINT(1) NOT NULL DEFAULT 0,
    unlock_time DATETIME NULL,
    sync_id VARCHAR(32) NOT NULL,
    is_current TINYINT(1) NOT NULL DEFAULT 1,
    synced_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_game_achievement_version (user_id, app_id, api_name, sync_id),
    KEY idx_user_game_achievement_current (user_id, app_id, is_current)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户 Steam 成就版本快照';

UPDATE t_user_steam_game
SET achievement_refresh_status = COALESCE(achievement_refresh_status, 'NOT_SYNCED'),
    achievement_fail_count = COALESCE(achievement_fail_count, 0);
