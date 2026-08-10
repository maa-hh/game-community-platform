USE game_community;
SET NAMES utf8mb4;

-- 游戏短评互动数据。短评/回复正文存 MongoDB，MySQL 只保存可排序、可计数的元数据。
CREATE TABLE IF NOT EXISTS t_social_game_review (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '内部主键，不出接口',
    review_id VARCHAR(32) NOT NULL COMMENT '游戏短评公开标识',
    app_id BIGINT NOT NULL COMMENT 'Steam App ID',
    like_count BIGINT NOT NULL DEFAULT 0 COMMENT '点赞数',
    reply_count BIGINT NOT NULL DEFAULT 0 COMMENT '回复数',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1正常 0删除',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_social_game_review_review (review_id),
    KEY idx_social_game_review_app_hot (app_id, status, like_count, create_time, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='游戏短评社交元数据';

CREATE TABLE IF NOT EXISTS t_social_game_review_like (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '内部主键，不出接口',
    review_id VARCHAR(32) NOT NULL,
    user_id BIGINT NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_social_game_review_like_user (review_id, user_id),
    KEY idx_social_game_review_like_review (review_id, create_time, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='游戏短评点赞';

CREATE TABLE IF NOT EXISTS t_social_game_review_reply (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '内部主键，不出接口',
    reply_id VARCHAR(32) NOT NULL COMMENT '回复公开标识',
    review_id VARCHAR(32) NOT NULL COMMENT '短评公开标识',
    user_id BIGINT NOT NULL,
    username VARCHAR(64) NOT NULL DEFAULT '',
    avatar VARCHAR(1024) NOT NULL DEFAULT '',
    like_count BIGINT NOT NULL DEFAULT 0,
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1正常 0删除',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_social_game_review_reply_public (reply_id),
    KEY idx_social_game_review_reply_review (review_id, status, create_time, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='游戏短评回复元数据';

CREATE TABLE IF NOT EXISTS t_social_game_review_reply_like (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '内部主键，不出接口',
    reply_id VARCHAR(32) NOT NULL,
    user_id BIGINT NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_social_game_review_reply_like_user (reply_id, user_id),
    KEY idx_social_game_review_reply_like_reply (reply_id, create_time, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='游戏短评回复点赞';

-- 为已存在的 Steam 短评建立可排序元数据；正文首次被 Steam 列表读取时同步到 MongoDB。
INSERT INTO t_social_game_review (review_id, app_id, like_count, reply_count, status, create_time, update_time)
SELECT review_id, app_id, 0, 0, status, create_time, update_time
FROM t_game_review
WHERE review_id IS NOT NULL AND review_id <> ''
ON DUPLICATE KEY UPDATE app_id = VALUES(app_id), status = VALUES(status);
