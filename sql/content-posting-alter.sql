-- 发帖模式与视频字段（已有库增量；若列已存在请跳过对应语句）
ALTER TABLE t_article
    ADD COLUMN post_type TINYINT NOT NULL DEFAULT 2 COMMENT '发帖模式: 1-图文, 2-文章, 3-视频' AFTER cover_url;

ALTER TABLE t_article
    ADD COLUMN video_url VARCHAR(1024) DEFAULT NULL COMMENT '主视频URL' AFTER post_type;
