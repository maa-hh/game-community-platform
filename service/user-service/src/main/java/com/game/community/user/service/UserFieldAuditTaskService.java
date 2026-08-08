package com.game.community.user.service;

/**
 * 用户字段审核任务（USERNAME / SIGNATURE / AVATAR）
 */
public interface UserFieldAuditTaskService {
    com.game.community.model.base.Result<Void> approveHumanReview(Long taskId);

    com.game.community.model.base.Result<Void> rejectHumanReview(Long taskId, String reason);

    com.game.community.model.vo.user.UserAuditTaskBriefVO getAuditTaskBrief(Long taskId);
}
