USE game_community;
SET NAMES utf8mb4;

ALTER TABLE t_user_steam_game
    ADD COLUMN name_zh VARCHAR(200) NULL COMMENT 'Steam 简体中文名称' AFTER name,
    ADD COLUMN name_en VARCHAR(200) NULL COMMENT 'Steam 英文名称' AFTER name_zh,
    ADD COLUMN cover_url VARCHAR(500) NULL COMMENT '无需详情接口即可展示的 Steam 头图' AFTER icon_url,
    ADD COLUMN sync_id VARCHAR(32) NULL COMMENT '最近一次成功更新该游戏的同步会话 ID' AFTER synced_at,
    ADD COLUMN is_owned TINYINT(1) NOT NULL DEFAULT 1 COMMENT '当前是否仍在 Steam 游戏库中' AFTER sync_id;

ALTER TABLE t_game_catalog
    ADD COLUMN name_zh VARCHAR(200) NULL COMMENT 'Steam 简体中文名称' AFTER steam_name,
    ADD COLUMN name_en VARCHAR(200) NULL COMMENT 'Steam 英文名称' AFTER name_zh;

UPDATE t_user_steam_game
SET name_zh = COALESCE(name_zh, name),
    name_en = COALESCE(name_en, name)
WHERE name_zh IS NULL OR name_en IS NULL;
