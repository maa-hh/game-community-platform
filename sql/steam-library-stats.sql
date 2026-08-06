-- Steam 游戏库：近两周时长、成就进度
USE game_community;
SET NAMES utf8mb4;

ALTER TABLE t_user_steam_game
    ADD COLUMN playtime_two_weeks INT NOT NULL DEFAULT 0 COMMENT '近两周游玩分钟' AFTER playtime_forever,
    ADD COLUMN achievement_unlocked INT NULL COMMENT '已解锁成就数' AFTER playtime_two_weeks,
    ADD COLUMN achievement_total INT NULL COMMENT '成就总数' AFTER achievement_unlocked;
