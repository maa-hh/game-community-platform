-- 评论/回复点赞计入热榜（各 +1）
ALTER TABLE t_article_behavior_event
    ADD COLUMN comment_like_delta BIGINT NOT NULL DEFAULT 0 AFTER share_delta,
    ADD COLUMN reply_like_delta BIGINT NOT NULL DEFAULT 0 AFTER comment_like_delta;
