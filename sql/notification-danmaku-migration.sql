-- 通知弹幕协议迁移：保存弹幕定位信息，并纳入评论/回复通知分类。

SET @has_danmaku_id := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 't_notification_message'
      AND column_name = 'danmaku_id'
);
SET @add_danmaku_id_sql := IF(
    @has_danmaku_id = 0,
    'ALTER TABLE t_notification_message ADD COLUMN danmaku_id BIGINT NULL COMMENT ''弹幕ID'' AFTER reply_id',
    'SELECT 1'
);
PREPARE add_danmaku_id FROM @add_danmaku_id_sql;
EXECUTE add_danmaku_id;
DEALLOCATE PREPARE add_danmaku_id;

SET @has_video_public_id := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 't_notification_message'
      AND column_name = 'video_public_id'
);
SET @add_video_public_id_sql := IF(
    @has_video_public_id = 0,
    'ALTER TABLE t_notification_message ADD COLUMN video_public_id VARCHAR(128) NULL COMMENT ''视频帖子公开ID'' AFTER danmaku_id',
    'SELECT 1'
);
PREPARE add_video_public_id FROM @add_video_public_id_sql;
EXECUTE add_video_public_id;
DEALLOCATE PREPARE add_video_public_id;

SET @has_danmaku_index := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 't_notification_message'
      AND index_name = 'idx_notification_user_danmaku_time'
);
SET @add_danmaku_index_sql := IF(
    @has_danmaku_index = 0,
    'ALTER TABLE t_notification_message ADD KEY idx_notification_user_danmaku_time (user_id, danmaku_id, create_time DESC, id DESC)',
    'SELECT 1'
);
PREPARE add_danmaku_index FROM @add_danmaku_index_sql;
EXECUTE add_danmaku_index;
DEALLOCATE PREPARE add_danmaku_index;
