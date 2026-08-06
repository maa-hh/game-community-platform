-- Steam 评价人数（appreviews total_reviews）
USE game_community;
SET NAMES utf8mb4;

ALTER TABLE t_game_catalog
    ADD COLUMN steam_review_count INT NULL COMMENT 'Steam 评价总数' AFTER steam_review_score;
