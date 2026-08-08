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

    /** 执行 approveHumanReview 对应的业务处理。 */
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
        FieldAuditPayload payload = auditHelper.readPayload(task);
        if (!auditTaskExecutor.applyPassed(task.getTaskType(), task.getUserId(), payload)) {
            auditTaskExecutor.rollbackField(task.getTaskType(), task.getUserId(), payload);
            auditTaskExecutor.updateTask(task.getId(), AuditTaskStatus.FAILED, null, "人工审核回写失败");
            throw new BusinessException("资料审核回写失败");
        }
        auditTaskExecutor.updateTask(task.getId(), AuditTaskStatus.PASSED, null, "");
        notificationProducer.publishPassed(task.getUserId(), task.getTaskType(),
                UserConstants.AuditScore.HUMAN_REVIEW_PASS, "人工审核通过");
        return Result.success(null);
    }

    /** 执行 rejectHumanReview 对应的业务处理。 */
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
        FieldAuditPayload payload = auditHelper.readPayload(task);
        auditTaskExecutor.rollbackField(task.getTaskType(), task.getUserId(), payload);
        String finalReason = StringUtils.hasText(reason) ? reason : "人工审核未通过";
        auditTaskExecutor.updateTask(task.getId(), AuditTaskStatus.REJECTED, null, finalReason);
        notificationProducer.publishRejected(task.getUserId(), task.getTaskType(),
                UserConstants.AuditScore.REJECT_MAX, finalReason);
        return Result.success(null);
    }

    /** 执行 getAuditTaskBrief 对应的业务处理。 */
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

    /** 执行 requireTask 对应的业务处理。 */
    private UserAuditTask requireTask(Long taskId) {
        UserAuditTask task = userAuditTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException("资料审核任务不存在");
        }
        return task;
    }
}
