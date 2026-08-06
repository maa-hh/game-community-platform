-- Steam 促销截止时间
USE game_community;
SET NAMES utf8mb4;

ALTER TABLE t_game_catalog
    ADD COLUMN price_discount_end_at BIGINT NULL COMMENT 'Steam 促销截止 Unix 秒' AFTER price_discount;
