-- 用户关注的游戏（我的游戏）
USE game_community;
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS t_user_game_follow (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id      BIGINT NOT NULL COMMENT '社区用户 ID',
    app_id       BIGINT NOT NULL COMMENT 'Steam appId',
    source       VARCHAR(32) NOT NULL DEFAULT 'manual' COMMENT 'manual/steam_import/discover',
    create_time  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_app (user_id, app_id),
    KEY idx_user_time (user_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户关注游戏';
