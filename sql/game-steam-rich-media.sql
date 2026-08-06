-- Steam 详情扩展：截图、预告片、价格、Metacritic、成就、配置
USE game_community;
SET NAMES utf8mb4;

ALTER TABLE t_game_catalog
    ADD COLUMN steam_screenshots JSON NULL COMMENT 'Steam 截图' AFTER steam_review_score,
    ADD COLUMN steam_movies JSON NULL COMMENT 'Steam 预告片' AFTER steam_screenshots,
    ADD COLUMN steam_categories JSON NULL COMMENT 'Steam 特性标签' AFTER steam_movies,
    ADD COLUMN steam_is_free TINYINT(1) NULL COMMENT '是否免费' AFTER steam_categories,
    ADD COLUMN price_currency VARCHAR(8) NULL AFTER steam_is_free,
    ADD COLUMN price_initial INT NULL COMMENT '原价分' AFTER price_currency,
    ADD COLUMN price_final INT NULL COMMENT '现价分' AFTER price_initial,
    ADD COLUMN price_discount INT NULL AFTER price_final,
    ADD COLUMN price_formatted VARCHAR(32) NULL AFTER price_discount,
    ADD COLUMN metacritic_score INT NULL AFTER price_formatted,
    ADD COLUMN metacritic_url VARCHAR(500) NULL AFTER metacritic_score,
    ADD COLUMN achievement_total INT NULL AFTER metacritic_url,
    ADD COLUMN achievement_highlights JSON NULL AFTER achievement_total,
    ADD COLUMN pc_requirements_min MEDIUMTEXT NULL AFTER achievement_highlights,
    ADD COLUMN pc_requirements_rec MEDIUMTEXT NULL AFTER pc_requirements_min;
