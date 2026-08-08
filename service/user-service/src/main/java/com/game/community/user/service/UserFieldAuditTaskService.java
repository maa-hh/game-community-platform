package com.game.community.user.service;

/**
 * 用户字段审核任务（USERNAME / SIGNATURE / AVATAR）
 */
public interface UserFieldAuditTaskService {
    /** 人工审核通过字段任务并回写资料。 */
    com.game.community.model.base.Result<Void> approveHumanReview(Long taskId);

    /** 人工审核拒绝字段任务并清理 pending 数据。 */
    com.game.community.model.base.Result<Void> rejectHumanReview(Long taskId, String reason);

    /** 查询人工审核任务摘要。 */
    com.game.community.model.vo.user.UserAuditTaskBriefVO getAuditTaskBrief(Long taskId);
}
