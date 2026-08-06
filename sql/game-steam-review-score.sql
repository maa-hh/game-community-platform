-- Steam 好评率（0–100），同步自 Steam appreviews API
USE game_community;
SET NAMES utf8mb4;

ALTER TABLE t_game_catalog
    ADD COLUMN steam_review_score INT NULL COMMENT 'Steam 好评率 0-100' AFTER avg_score;
