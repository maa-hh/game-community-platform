-- Steam 游戏库：最后游玩时间
USE game_community;
SET NAMES utf8mb4;

ALTER TABLE t_user_steam_game
    ADD COLUMN last_played_at DATETIME NULL COMMENT '最后游玩时间' AFTER playtime_two_weeks;
