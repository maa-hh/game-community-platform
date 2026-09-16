package com.game.community.user.service.impl;

import com.game.community.common.exception.BusinessException;
import com.game.community.common.constant.user.UserConstants;
import com.game.community.model.base.Result;
import com.game.community.model.entity.user.User;
import com.game.community.model.entity.user.UserAuditTask;
import com.game.community.model.enums.user.AuditFieldType;
import com.game.community.model.enums.user.AuditTaskStatus;
import com.game.community.model.payload.user.FieldAuditPayload;
import com.game.community.model.vo.user.UserAuditTaskBriefVO;
import com.game.community.user.common.audit.UserAuditHelper;
import com.game.community.user.event.executor.AuditTaskExecutor;
import com.game.community.user.event.kafka.ProfileAuditNotificationProducer;
import com.game.community.user.mapper.UserAuditTaskMapper;
import com.game.community.user.mapper.UserMapper;
import com.game.community.user.service.UserFieldAuditTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 审核业务入口；具体审核任务由 AuditTaskExecutor 在线程池中执行。 */
@Service
@RequiredArgsConstructor
public class UserFieldAuditTaskServiceImpl implements UserFieldAuditTaskService {

    private final UserAuditTaskMapper userAuditTaskMapper;
    private final UserMapper userMapper;
    private final UserAuditHelper auditHelper;
    private final ProfileAuditNotificationProducer notificationProducer;
    private final AuditTaskExecutor auditTaskExecutor;

    /** 抢占人工审核任务，回写通过资料并推进任务终态。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> approveHumanReview(Long taskId) {
        UserAuditTask task = requireTask(taskId);
        if (task.getStatus() == AuditTaskStatus.PASSED) {
            return Result.success(null);
        }
        if (task.getStatus() != AuditTaskStatus.HUMAN_REVIEW) {
            throw new BusinessException("资料审核任务状态不可处理");
        }
        if (!auditTaskExecutor.claimHumanReview(taskId)) {
            throw new BusinessException("资料审核任务已被其他操作处理");
        }
        FieldAuditPayload payload = auditHelper.readPayload(task);
        if (!auditTaskExecutor.applyPassed(task.getTaskType(), task.getUserId(), payload)) {
            auditTaskExecutor.rollbackField(task.getTaskType(), task.getUserId(), payload);
            auditTaskExecutor.updateTaskIfStatus(task.getId(), AuditTaskStatus.PROCESSING,
                    AuditTaskStatus.FAILED, null, "人工审核回写失败");
            throw new BusinessException("资料审核回写失败");
        }
        if (!auditTaskExecutor.updateTaskIfStatus(task.getId(), AuditTaskStatus.PROCESSING,
                AuditTaskStatus.PASSED, null, "")) {
            throw new BusinessException("资料审核任务状态已变化");
        }
        notificationProducer.publishPassed(task.getUserId(), task.getTaskType(),
                UserConstants.AuditScore.HUMAN_REVIEW_PASS, "人工审核通过");
        return Result.success(null);
    }

    /** 抢占人工审核任务，清理待审字段并推进拒绝终态。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> rejectHumanReview(Long taskId, String reason) {
        UserAuditTask task = requireTask(taskId);
        if (task.getStatus() == AuditTaskStatus.REJECTED) {
            return Result.success(null);
        }
        if (task.getStatus() != AuditTaskStatus.HUMAN_REVIEW) {
            throw new BusinessException("资料审核任务状态不可处理");
        }
        if (!auditTaskExecutor.claimHumanReview(taskId)) {
            throw new BusinessException("资料审核任务已被其他操作处理");
        }
        FieldAuditPayload payload = auditHelper.readPayload(task);
        auditTaskExecutor.rollbackField(task.getTaskType(), task.getUserId(), payload);
        String finalReason = StringUtils.hasText(reason) ? reason : "人工审核未通过";
        if (!auditTaskExecutor.updateTaskIfStatus(task.getId(), AuditTaskStatus.PROCESSING,
                AuditTaskStatus.REJECTED, null, finalReason)) {
            throw new BusinessException("资料审核任务状态已变化");
        }
        notificationProducer.publishRejected(task.getUserId(), task.getTaskType(),
                UserConstants.AuditScore.REJECT_MAX, finalReason);
        return Result.success(null);
    }

    /** 查询人工审核所需的任务摘要，不暴露完整内部负载。 */
    @Override
    public UserAuditTaskBriefVO getAuditTaskBrief(Long taskId) {
        UserAuditTask task = userAuditTaskMapper.selectById(taskId);
        if (task == null) {
            return null;
        }
        UserAuditTaskBriefVO vo = new UserAuditTaskBriefVO();
        vo.setId(task.getId());
        User user = userMapper.selectById(task.getUserId());
        vo.setAccountId(user == null ? null : user.getAccountId());
        vo.setFieldType(task.getTaskType() == null ? null : task.getTaskType().label());
        vo.setStatus(task.getStatus() == null ? null : task.getStatus().name());
        vo.setUpdateTime(task.getUpdateTime());
        return vo;
    }

    /** 查询并校验审核任务存在。 */
    private UserAuditTask requireTask(Long taskId) {
        UserAuditTask task = userAuditTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException("资料审核任务不存在");
        }
        return task;
    }
}
