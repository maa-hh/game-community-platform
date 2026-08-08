package com.game.community.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.community.common.exception.BusinessException;
import com.game.community.model.base.Result;
import com.game.community.model.entity.user.User;
import com.game.community.model.entity.user.UserAuditRejectLog;
import com.game.community.model.entity.user.UserAuditTask;
import com.game.community.model.vo.user.UserAuditTaskBriefVO;
import com.game.community.model.enums.user.AuditFieldType;
import com.game.community.model.enums.user.AuditTaskStatus;
import com.game.community.model.enums.user.UserStrings;
import com.game.community.model.payload.user.FieldAuditPayload;
import com.game.community.user.audit.UserAuditHelper;
import com.game.community.user.event.ModerationTaskProducer;
import com.game.community.user.event.ProfileAuditNotificationProducer;
import com.game.community.user.mapper.UserAuditRejectLogMapper;
import com.game.community.user.mapper.UserAuditTaskMapper;
import com.game.community.user.mapper.UserMapper;
import com.game.community.user.service.UserFieldAuditTaskService;
import com.game.community.utils.DfaAuditUtils;
import com.game.community.utils.MinIOUtils;
import com.game.community.utils.audit.AuditClient;
import com.game.community.utils.audit.AuditResult;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 字段审核：入队审核线程池 → 打分 → 通过/拒绝/人工复核 → 推送
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserFieldAuditTaskServiceImpl implements UserFieldAuditTaskService {

    private final UserAuditTaskMapper userAuditTaskMapper;
    private final UserAuditRejectLogMapper userAuditRejectLogMapper;
    private final UserMapper userMapper;
    private final UserAuditHelper auditHelper;
    private final DfaAuditUtils dfaAuditUtils;
    private final AuditClient auditClient;
    private final MinIOUtils minIOUtils;
    private final ProfileAuditNotificationProducer notificationProducer;

    private final ModerationTaskProducer moderationTaskProducer;
    private final ObjectMapper objectMapper;

    @Resource(name = "auditExecutor")
    private Executor auditExecutor;

    @Override
    public void enqueueFieldAudit(Long taskId) {
        auditExecutor.execute(() -> auditField(taskId));
    }

    @Override
    public void handleEnqueueRejected(Long taskId, AuditFieldType taskType, Long userId, Object requestPayload,
                                      RejectedExecutionException cause) {
        UserAuditTask task = taskId == null ? null : userAuditTaskMapper.selectById(taskId);
        FieldAuditPayload payload = task == null ? null : auditHelper.readPayload(task);
        if (task != null) {
            failTaskQuietly(task, payload, "审核服务繁忙，队列已满");
        } else if (taskType != null && userId != null) {
            rollbackField(taskType, userId, null);
        }

        recordRejectLog(taskId, taskType, userId, requestPayload, cause);
    }

    @Override
    public void auditField(Long taskId) {
        UserAuditTask task = userAuditTaskMapper.selectById(taskId);
        if (task == null || task.getStatus() != AuditTaskStatus.PENDING) {
            return;
        }
        if (!claimTask(taskId)) {
            return;
        }

        FieldAuditPayload payload = auditHelper.readPayload(task);
        if (payload == null || payload.getField() == null) {
            failTask(task, null, "审核任务负载异常");
            return;
        }

        try {
            AuditFieldResult result = runAudit(task.getTaskType(), payload);
            completeByScore(task, payload, result);
        } catch (Exception e) {
            failTask(task, payload, abbreviate("字段审核异常: " + e.getMessage()));
            log.warn("字段审核异常: taskId={}, type={}, error={}", taskId, task.getTaskType(), e.getMessage(), e);
        }
    }

    private AuditFieldResult auditTextField(String fieldLabel, String text) {
        if (!StringUtils.hasText(text)) {
            return new AuditFieldResult(10, "空内容");
        }
        if (!dfaAuditUtils.pass(text)) {
            log.warn("{}本地敏感词未通过: {}", fieldLabel, text);
            return new AuditFieldResult(1, fieldLabel + "包含敏感词");
        }
        return toFieldResult(fieldLabel, auditClient.auditText(text));
    }

    private AuditFieldResult auditAvatarUrl(String avatarUrl) {
        AuditResult result;
        try {
            MinIOUtils.FilePayload filePayload = minIOUtils.readFileByUrl(avatarUrl);
            result = auditClient.auditImage(filePayload.bytes(), filePayload.contentType());
        } catch (Exception e) {
            log.warn("头像图片读取失败，回退为 URL 审核: url={}, error={}", avatarUrl, e.getMessage());
            result = auditClient.auditImageUrl(avatarUrl);
        }
        return toFieldResult("头像", result);
    }

    private AuditFieldResult toFieldResult(String fieldLabel, AuditResult result) {
        if (result == null) {
            return new AuditFieldResult(1, fieldLabel + "审核结果为空");
        }
        String reason = StringUtils.hasText(result.getReason()) ? result.getReason() : fieldLabel + "审核未通过";
        Integer score = result.getScore();
        if (score == null) {
            score = result.isPass() ? 9 : 1;
        }
        log.info("{}审核完成: score={}, reason={}", fieldLabel, score, reason);
        return new AuditFieldResult(score, reason);
    }

    private record AuditFieldResult(Integer score, String reason) {
        boolean pass() {
            return score != null && score >= 7;
        }

        boolean humanReview() {
            return score != null && score >= 4 && score <= 6;
        }

        boolean reject() {
            return score == null || score <= 3;
        }
    }

    private void recordRejectLog(Long taskId, AuditFieldType taskType, Long userId, Object requestPayload,
                                 RejectedExecutionException cause) {
        try {
            User user = userId == null ? null : userMapper.selectById(userId);
            UserAuditRejectLog rejectLog = new UserAuditRejectLog();
            rejectLog.setUserId(userId);
            rejectLog.setAccountId(user == null ? null : user.getAccountId());
            rejectLog.setTaskId(taskId);
            rejectLog.setTaskType(taskType);
            rejectLog.setRequestData(toJson(requestPayload));
            rejectLog.setRejectReason(abbreviate(
                    cause == null || !StringUtils.hasText(cause.getMessage())
                            ? "审核服务繁忙，线程池队列已满"
                            : cause.getMessage()));
            rejectLog.setPoolSnapshot(poolSnapshot());
            rejectLog.setCreateTime(LocalDateTime.now());
            userAuditRejectLogMapper.insert(rejectLog);
        } catch (Exception e) {
            log.error("写入审核拒绝日志失败: taskId={}, userId={}", taskId, userId, e);
        }
    }

    private String poolSnapshot() {
        if (!(auditExecutor instanceof ThreadPoolTaskExecutor taskExecutor)) {
            return "executor=" + auditExecutor.getClass().getSimpleName();
        }
        ThreadPoolExecutor pool = taskExecutor.getThreadPoolExecutor();
        if (pool == null) {
            return "uninitialized";
        }
        return "active=" + pool.getActiveCount()
                + ",pool=" + pool.getPoolSize()
                + ",core=" + pool.getCorePoolSize()
                + ",max=" + pool.getMaximumPoolSize()
                + ",queue=" + pool.getQueue().size()
                + ",queueCapacity=" + (pool.getQueue().size() + pool.getQueue().remainingCapacity())
                + ",completed=" + pool.getCompletedTaskCount();
    }

    private String toJson(Object payload) {
        if (payload == null) {
            return "{}";
        }
        try {
            if (payload instanceof String text) {
                return text;
            }
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            return String.valueOf(payload);
        }
    }

    private AuditFieldResult runAudit(AuditFieldType taskType, FieldAuditPayload payload) {
        if (taskType == AuditFieldType.AVATAR) {
            String previewUrl = minIOUtils.generatePrivateAvatarUrl(payload.getPendingObjectName());
            return auditAvatarUrl(previewUrl);
        }
        return auditTextField(taskType.label(), payload.getContent());
    }

    private void completeByScore(UserAuditTask task, FieldAuditPayload payload,
                                 AuditFieldResult result) {
        Integer score = result.score();
        String reason = result.reason();
        updateTaskScore(task.getId(), score, reason);

        if (result.reject()) {
            rollbackField(task.getTaskType(), task.getUserId(), payload);
            finishTask(task.getId(), AuditTaskStatus.REJECTED, reason);
            notificationProducer.publishRejected(task.getUserId(), task.getTaskType(), score, reason);
            log.warn("字段审核拒绝: taskId={}, field={}, score={}, reason={}",
                    task.getId(), task.getTaskType(), score, reason);
            return;
        }

        if (result.humanReview()) {
            markHumanReview(task.getTaskType(), task.getUserId());
            finishTask(task.getId(), AuditTaskStatus.HUMAN_REVIEW, reason);
            notificationProducer.publishHumanReview(task.getUserId(), task.getTaskType(), score, reason);
            UserAuditTask refreshed = userAuditTaskMapper.selectById(task.getId());
            moderationTaskProducer.publishProfileAudit(
                    task.getId(),
                    task.getUserId(),
                    task.getTaskType().label(),
                    payload == null ? null : payload.getContent(),
                    reason,
                    refreshed == null ? LocalDateTime.now() : refreshed.getUpdateTime());
            log.info("字段进入人工审核: taskId={}, field={}, score={}", task.getId(), task.getTaskType(), score);
            return;
        }

        if (!applyPassed(task.getTaskType(), task.getUserId(), payload)) {
            rollbackField(task.getTaskType(), task.getUserId(), payload);
            finishTask(task.getId(), AuditTaskStatus.FAILED, "审核结果回写失败");
            notificationProducer.publishRejected(task.getUserId(), task.getTaskType(), score, "审核结果回写失败");
            return;
        }
        finishTask(task.getId(), AuditTaskStatus.PASSED, UserStrings.EMPTY);
        notificationProducer.publishPassed(task.getUserId(), task.getTaskType(), score, reason);
        log.info("字段审核通过: taskId={}, field={}, score={}", task.getId(), task.getTaskType(), score);
    }

    private boolean applyPassed(AuditFieldType taskType, Long userId, FieldAuditPayload payload) {
        return switch (taskType) {
            case USERNAME -> auditHelper.applyUsernamePassed(userId, payload.getUserVersion(), payload.getContent());
            case SIGNATURE -> auditHelper.applySignaturePassed(userId, payload.getUserVersion(), payload.getContent());
            case AVATAR -> applyAvatarPassed(userId, payload);
        };
    }

    private boolean applyAvatarPassed(Long userId, FieldAuditPayload payload) {
        String publicUrl = minIOUtils.publishPrivateAvatar(payload.getPendingObjectName());
        boolean updated = auditHelper.applyAvatarPassed(
                userId, payload.getUserVersion(), payload.getPendingObjectName(), publicUrl);
        if (!updated) {
            minIOUtils.deletePrivateAvatar(payload.getPendingObjectName());
            minIOUtils.deletePublicAvatarByUrl(publicUrl);
            return false;
        }
        minIOUtils.deletePrivateAvatar(payload.getPendingObjectName());
        deleteQuietly(payload.getOldAvatarUrl());
        return true;
    }

    private void rollbackField(AuditFieldType taskType, Long userId, FieldAuditPayload payload) {
        switch (taskType) {
            case USERNAME -> auditHelper.clearUsernameAudit(userId,
                    payload == null ? null : payload.getContent());
            case SIGNATURE -> auditHelper.clearSignatureAudit(userId,
                    payload == null ? null : payload.getContent());
            case AVATAR -> {
                auditHelper.clearAvatarAudit(userId,
                        payload == null ? null : payload.getPendingObjectName());
                if (payload != null && StringUtils.hasText(payload.getPendingObjectName())) {
                    try {
                        minIOUtils.deletePrivateAvatar(payload.getPendingObjectName());
                    } catch (Exception e) {
                        log.warn("删除待审头像失败: object={}", payload.getPendingObjectName(), e);
                    }
                }
            }
        }
    }

    private void markHumanReview(AuditFieldType taskType, Long userId) {
        switch (taskType) {
            case USERNAME -> auditHelper.markUsernameHumanReview(userId);
            case SIGNATURE -> auditHelper.markSignatureHumanReview(userId);
            case AVATAR -> auditHelper.markAvatarHumanReview(userId);
        }
    }

    private void failTask(UserAuditTask task, FieldAuditPayload payload, String message) {
        rollbackField(task.getTaskType(), task.getUserId(), payload);
        finishTaskFromAny(task.getId(), AuditTaskStatus.FAILED, message);
        notificationProducer.publishRejected(task.getUserId(), task.getTaskType(), 0, message);
    }

    private void failTaskQuietly(UserAuditTask task, FieldAuditPayload payload, String message) {
        rollbackField(task.getTaskType(), task.getUserId(), payload);
        finishTaskFromAny(task.getId(), AuditTaskStatus.FAILED, message);
    }

    private boolean claimTask(Long taskId) {
        return userAuditTaskMapper.update(null, new LambdaUpdateWrapper<UserAuditTask>()
                .eq(UserAuditTask::getId, taskId)
                .eq(UserAuditTask::getStatus, AuditTaskStatus.PENDING)
                .set(UserAuditTask::getStatus, AuditTaskStatus.PROCESSING)
                .set(UserAuditTask::getUpdateTime, LocalDateTime.now())) > 0;
    }

    private void updateTaskScore(Long taskId, Integer score, String reason) {
        userAuditTaskMapper.update(null, new LambdaUpdateWrapper<UserAuditTask>()
                .eq(UserAuditTask::getId, taskId)
                .set(UserAuditTask::getScore, score)
                .set(UserAuditTask::getErrorMessage, abbreviate(reason))
                .set(UserAuditTask::getUpdateTime, LocalDateTime.now()));
    }

    private void finishTask(Long taskId, AuditTaskStatus status, String errorMessage) {
        userAuditTaskMapper.update(null, new LambdaUpdateWrapper<UserAuditTask>()
                .eq(UserAuditTask::getId, taskId)
                .eq(UserAuditTask::getStatus, AuditTaskStatus.PROCESSING)
                .set(UserAuditTask::getStatus, status)
                .set(UserAuditTask::getErrorMessage, abbreviate(errorMessage))
                .set(UserAuditTask::getUpdateTime, LocalDateTime.now()));
    }

    private void finishTaskFromAny(Long taskId, AuditTaskStatus status, String errorMessage) {
        userAuditTaskMapper.update(null, new LambdaUpdateWrapper<UserAuditTask>()
                .eq(UserAuditTask::getId, taskId)
                .set(UserAuditTask::getStatus, status)
                .set(UserAuditTask::getErrorMessage, abbreviate(errorMessage))
                .set(UserAuditTask::getUpdateTime, LocalDateTime.now()));
    }

    private void deleteQuietly(String fileUrl) {
        if (!StringUtils.hasText(fileUrl)) {
            return;
        }
        try {
            minIOUtils.deletePublicAvatarByUrl(fileUrl);
        } catch (Exception e) {
            log.warn("删除旧头像失败: {}", fileUrl, e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> approveHumanReview(Long taskId) {
        UserAuditTask task = userAuditTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException("资料审核任务不存在");
        }
        if (task.getStatus() == AuditTaskStatus.PASSED) {
            return Result.success(null);
        }
        if (task.getStatus() != AuditTaskStatus.HUMAN_REVIEW) {
            throw new BusinessException("资料审核任务状态不可处理");
        }
        FieldAuditPayload payload = auditHelper.readPayload(task);
        if (!applyPassed(task.getTaskType(), task.getUserId(), payload)) {
            rollbackField(task.getTaskType(), task.getUserId(), payload);
            finishTaskFromAny(task.getId(), AuditTaskStatus.FAILED, "人工审核回写失败");
            throw new BusinessException("资料审核回写失败");
        }
        finishTaskFromAny(task.getId(), AuditTaskStatus.PASSED, UserStrings.EMPTY);
        notificationProducer.publishPassed(task.getUserId(), task.getTaskType(), 8, "人工审核通过");
        return Result.success(null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> rejectHumanReview(Long taskId, String reason) {
        UserAuditTask task = userAuditTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException("资料审核任务不存在");
        }
        if (task.getStatus() == AuditTaskStatus.REJECTED) {
            return Result.success(null);
        }
        if (task.getStatus() != AuditTaskStatus.HUMAN_REVIEW) {
            throw new BusinessException("资料审核任务状态不可处理");
        }
        FieldAuditPayload payload = auditHelper.readPayload(task);
        rollbackField(task.getTaskType(), task.getUserId(), payload);
        String finalReason = StringUtils.hasText(reason) ? reason : "人工审核未通过";
        finishTaskFromAny(task.getId(), AuditTaskStatus.REJECTED, finalReason);
        notificationProducer.publishRejected(task.getUserId(), task.getTaskType(), 3, finalReason);
        return Result.success(null);
    }

    private String abbreviate(String message) {
        if (!StringUtils.hasText(message)) {
            return UserStrings.EMPTY;
        }
        if (message.length() <= 255) {
            return message;
        }
        return message.substring(0, 255);
    }

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
}
