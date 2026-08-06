-- accountId 对外化：清理冲突数据 + 展示字段 NOT NULL 默认值
-- 执行：mysql -h127.0.0.1 -P3307 -uroot -p game_community < sql/account-id-public-reset.sql

USE game_community;
SET NAMES utf8mb4;

-- 通知：清空历史（旧 VO 含 userId，重置后由新事件写入）
TRUNCATE TABLE t_notification_message;
UPDATE t_notification_user_state SET unread_notification_count = 0, feed_unread_flag = 0;

-- Steam 绑定：展示字段不允许 NULL
UPDATE t_user_steam_bind SET persona_name = '' WHERE persona_name IS NULL;
UPDATE t_user_steam_bind SET avatar_url = '' WHERE avatar_url IS NULL;
UPDATE t_user_steam_bind SET profile_url = '' WHERE profile_url IS NULL;
UPDATE t_user_steam_bind SET steam_level = 0 WHERE steam_level IS NULL;

ALTER TABLE t_user_steam_bind
    MODIFY persona_name VARCHAR(100) NOT NULL DEFAULT '',
    MODIFY avatar_url VARCHAR(500) NOT NULL DEFAULT '',
    MODIFY profile_url VARCHAR(500) NOT NULL DEFAULT '',
    MODIFY steam_level INT NOT NULL DEFAULT 0;

-- Steam 游戏库缓存：展示字段不允许 NULL
UPDATE t_user_steam_game SET name = '' WHERE name IS NULL;
UPDATE t_user_steam_game SET icon_url = '' WHERE icon_url IS NULL;
UPDATE t_user_steam_game SET achievement_unlocked = 0 WHERE achievement_unlocked IS NULL;
UPDATE t_user_steam_game SET achievement_total = 0 WHERE achievement_total IS NULL;

ALTER TABLE t_user_steam_game
    MODIFY name VARCHAR(200) NOT NULL DEFAULT '',
    MODIFY icon_url VARCHAR(500) NOT NULL DEFAULT '',
    MODIFY achievement_unlocked INT NOT NULL DEFAULT 0,
    MODIFY achievement_total INT NOT NULL DEFAULT 0;
