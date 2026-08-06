-- 已有库增量：审核目标快照字段
ALTER TABLE t_moderation_task
    ADD COLUMN target_status_snapshot VARCHAR(64) DEFAULT NULL COMMENT '目标状态快照，如 ARTICLE_PENDING/PROFILE_HUMAN_REVIEW' AFTER extra_payload,
    ADD COLUMN target_updated_at DATETIME DEFAULT NULL COMMENT '目标内容更新时间快照' AFTER target_status_snapshot;
