USE game_community;

/* 游戏评价社交与通知字段；可重复执行。 */
SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 't_notification_message'
       AND column_name = 'game_app_id') = 0,
    'ALTER TABLE t_notification_message ADD COLUMN game_app_id BIGINT NULL COMMENT ''游戏 App ID'' AFTER reply_id',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 't_notification_message'
       AND column_name = 'game_review_id') = 0,
    'ALTER TABLE t_notification_message ADD COLUMN game_review_id VARCHAR(64) NULL COMMENT ''游戏评价公开 ID'' AFTER game_app_id',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 't_notification_message'
       AND column_name = 'game_review_reply_id') = 0,
    'ALTER TABLE t_notification_message ADD COLUMN game_review_reply_id VARCHAR(64) NULL COMMENT ''游戏评价回复公开 ID'' AFTER game_review_id',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 't_social_game_review'
       AND column_name = 'user_id') = 0,
    'ALTER TABLE t_social_game_review ADD COLUMN user_id BIGINT NULL COMMENT ''评价作者用户 ID'' AFTER app_id',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE t_social_game_review review
JOIN t_game_review source ON source.review_id = review.review_id
SET review.user_id = source.user_id
WHERE review.user_id IS NULL;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 't_social_game_review_reply'
       AND column_name = 'reply_to_user_id') = 0,
    'ALTER TABLE t_social_game_review_reply ADD COLUMN reply_to_user_id BIGINT NULL COMMENT ''被回复用户 ID'' AFTER user_id',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 't_social_game_review_reply'
       AND column_name = 'reply_to_username') = 0,
    'ALTER TABLE t_social_game_review_reply ADD COLUMN reply_to_username VARCHAR(64) NULL COMMENT ''被回复用户名快照'' AFTER reply_to_user_id',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
