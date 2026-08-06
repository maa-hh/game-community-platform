package com.game.community.user.service;

import com.game.community.model.enums.user.AuditFieldType;

/**
 * 用户字段审核任务（USERNAME / SIGNATURE / AVATAR）
 */
public interface UserFieldAuditTaskService {

    /**
     * 将任务提交到审核线程池。
     * 队列满时抛出 {@link java.util.concurrent.RejectedExecutionException}。
     */
    void enqueueFieldAudit(Long taskId);

    /** 执行字段审核（由线程池调用） */
    void auditField(Long taskId);

    /**
     * 入队被拒绝：写拒绝日志、任务 FAILED、回滚字段占用。
     */
    void handleEnqueueRejected(Long taskId, AuditFieldType taskType, Long userId, Object requestPayload,
                               java.util.concurrent.RejectedExecutionException cause);

    com.game.community.model.base.Result<Void> approveHumanReview(Long taskId);

    com.game.community.model.base.Result<Void> rejectHumanReview(Long taskId, String reason);

    com.game.community.model.vo.user.UserAuditTaskBriefVO getAuditTaskBrief(Long taskId);
}
