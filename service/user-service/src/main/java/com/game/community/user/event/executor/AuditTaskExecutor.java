package com.game.community.user.event.executor;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.community.common.constant.user.UserConstants;
import com.game.community.feign.AiAgentFeignClient;
import com.game.community.model.base.Result;
import com.game.community.model.dto.aiagent.ModerationRequest;
import com.game.community.model.entity.user.User;
import com.game.community.model.entity.user.UserAuditRejectLog;
import com.game.community.model.entity.user.UserAuditTask;
import com.game.community.model.enums.user.AuditFieldType;
import com.game.community.model.enums.user.AuditTaskStatus;
import com.game.community.model.enums.user.UserStrings;
import com.game.community.model.enums.aiagent.ModerationCheckResult;
import com.game.community.model.enums.aiagent.ModerationContentType;
import com.game.community.model.enums.aiagent.ModerationDecision;
import com.game.community.model.payload.user.FieldAuditPayload;
import com.game.community.model.vo.aiagent.ModerationResultVO;
import com.game.community.user.common.audit.UserAuditHelper;
import com.game.community.user.event.kafka.ModerationTaskProducer;
import com.game.community.user.event.kafka.ProfileAuditNotificationProducer;
import com.game.community.user.mapper.UserAuditRejectLogMapper;
import com.game.community.user.mapper.UserAuditTaskMapper;
import com.game.community.user.mapper.UserMapper;
import com.game.community.utils.MinIOUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/** 审核任务入口：入队、审核、回写结果和发送通知。 */
@Slf4j
@Component
public class AuditTaskExecutor {

    private final Executor auditExecutor;
    private final UserAuditTaskMapper taskMapper;
    private final UserAuditRejectLogMapper rejectLogMapper;
    private final UserMapper userMapper;
    private final UserAuditHelper auditHelper;
    private final AiAgentFeignClient aiAgentFeignClient;
    private final MinIOUtils minIOUtils;
    private final ProfileAuditNotificationProducer notificationProducer;
    private final ModerationTaskProducer moderationTaskProducer;
    private final ObjectMapper objectMapper;

    /** 构造审核执行器并注入共享任务、审核和通知组件。 */
    public AuditTaskExecutor(@Qualifier("auditExecutor") Executor auditExecutor,
                             UserAuditTaskMapper taskMapper,
                             UserAuditRejectLogMapper rejectLogMapper,
                             UserMapper userMapper,
                             UserAuditHelper auditHelper,
                             AiAgentFeignClient aiAgentFeignClient,
                             MinIOUtils minIOUtils,
                             ProfileAuditNotificationProducer notificationProducer,
                             ModerationTaskProducer moderationTaskProducer,
                             ObjectMapper objectMapper) {
        this.auditExecutor = auditExecutor;
        this.taskMapper = taskMapper;
        this.rejectLogMapper = rejectLogMapper;
        this.userMapper = userMapper;
        this.auditHelper = auditHelper;
        this.aiAgentFeignClient = aiAgentFeignClient;
        this.minIOUtils = minIOUtils;
        this.notificationProducer = notificationProducer;
        this.moderationTaskProducer = moderationTaskProducer;
        this.objectMapper = objectMapper;
    }

    /** 原子领取待审任务后提交到本实例审核线程池。 */
    public boolean submit(Long taskId) {
        if (!claimPending(taskId)) {
            return false;
        }
        // 先把任务置为 PROCESSING 再入队，避免定时恢复器重复提交仍为 PENDING 的任务。
        auditExecutor.execute(() -> processClaimed(taskId));
        return true;
    }

    /** 队列满时释放字段占用、结束任务，并保留拒绝请求供排查。 */
    public void handleRejected(Long taskId, AuditFieldType taskType, Long userId,
                               Object requestPayload, RejectedExecutionException cause) {
        UserAuditTask task = taskId == null ? null : taskMapper.selectById(taskId);
        FieldAuditPayload payload = task == null ? null : auditHelper.readPayload(task);
        if (task != null) {
            rollbackField(task.getTaskType(), task.getUserId(), payload);
            boolean failed = updateTaskIfStatus(task.getId(), AuditTaskStatus.PROCESSING,
                    AuditTaskStatus.FAILED, null, "审核服务繁忙，队列已满");
            if (!failed) {
                updateTaskIfStatus(task.getId(), AuditTaskStatus.PENDING,
                        AuditTaskStatus.FAILED, null, "审核服务繁忙，队列已满");
            }
        } else if (taskType != null && userId != null) {
            rollbackField(taskType, userId, null);
        }
        try {
            User user = userId == null ? null : userMapper.selectById(userId);
            UserAuditRejectLog rejectLog = new UserAuditRejectLog();
            rejectLog.setUserId(userId);
            rejectLog.setAccountId(user == null ? null : user.getAccountId());
            rejectLog.setTaskId(taskId);
            rejectLog.setTaskType(taskType);
            rejectLog.setRequestData(requestPayload == null ? "{}"
                    : requestPayload instanceof String text
                    ? text : objectMapper.writeValueAsString(requestPayload));
            String rejectReason = cause == null || !StringUtils.hasText(cause.getMessage())
                    ? "审核服务繁忙，队列已满" : cause.getMessage();
            rejectLog.setRejectReason(abbreviate(rejectReason));
            rejectLog.setPoolSnapshot(UserStrings.EMPTY);
            rejectLog.setCreateTime(LocalDateTime.now());
            rejectLogMapper.insert(rejectLog);
        } catch (Exception e) {
            log.error("写入审核拒绝日志失败: taskId={}, userId={}", taskId, userId, e);
        }
    }

    /** 供直接调用方原子领取任务后同步完成审核。 */
    public void run(Long taskId) {
        if (claimPending(taskId)) {
            processClaimed(taskId);
        }
    }

    /** 仅处理已经由当前提交方 CAS 领取的任务。 */
    private void processClaimed(Long taskId) {
        UserAuditTask task = taskMapper.selectById(taskId);
        if (task == null) {
            return;
        }
        if (task.getTaskType() == null || !UserConstants.SUPPORTED_AUDIT_FIELDS.contains(task.getTaskType())) {
            updateTaskIfStatus(taskId, AuditTaskStatus.PROCESSING, AuditTaskStatus.FAILED,
                    null, "审核任务类型无效");
            log.warn("结束无效审核任务: taskId={}, taskType={}", taskId, task.getTaskType());
            return;
        }
        if (task.getStatus() != AuditTaskStatus.PROCESSING) {
            return;
        }

        FieldAuditPayload payload = auditHelper.readPayload(task);
        if (payload == null || payload.getField() == null) {
            rollbackField(task.getTaskType(), task.getUserId(), payload);
            updateTaskIfStatus(taskId, AuditTaskStatus.PROCESSING,
                    AuditTaskStatus.FAILED, null, "审核任务负载异常");
            notificationProducer.publishRejected(task.getUserId(), task.getTaskType(),
                    UserConstants.AuditScore.MIN, "审核任务负载异常");
            return;
        }

        try {
            AuditFieldResult result;
            if (task.getTaskType() == AuditFieldType.AVATAR) {
                String url = minIOUtils.generatePrivateAvatarUrl(payload.getPendingObjectName());
                ModerationRequest request = new ModerationRequest();
                request.setType(ModerationContentType.IMAGE);
                try {
                    MinIOUtils.FilePayload file = minIOUtils.readFileByUrl(url);
                    request.setImageBase64(Base64.getEncoder().encodeToString(file.bytes()));
                    request.setMimeType(file.contentType());
                } catch (Exception e) {
                    log.warn("头像图片读取失败，回退为 URL 审核: url={}", url, e);
                    request.setImageUrl(url);
                }
                result = result("头像", moderate(request));
            } else if (!StringUtils.hasText(payload.getContent())) {
                result = new AuditFieldResult(UserConstants.AuditScore.MAX, "空内容");
            } else {
                ModerationRequest request = new ModerationRequest();
                request.setType(ModerationContentType.TEXT);
                request.setContent(payload.getContent());
                result = result(task.getTaskType().label(), moderate(request));
            }

            Integer score = result.score();
            String reason = result.reason();
            // 先保存审核分数，再按分数推进最终状态，便于人工排查和后续通知。
            if (!updateTaskIfStatus(taskId, AuditTaskStatus.PROCESSING,
                    AuditTaskStatus.PROCESSING, score, reason)) {
                return;
            }
            if (result.reject()) {
                rollbackField(task.getTaskType(), task.getUserId(), payload);
                if (updateTaskIfStatus(taskId, AuditTaskStatus.PROCESSING,
                        AuditTaskStatus.REJECTED, null, reason)) {
                    notificationProducer.publishRejected(task.getUserId(), task.getTaskType(), score, reason);
                }
                return;
            }
            if (result.humanReview()) {
                // 人工复核保留 pending 内容，不清理占用，等待人工接口继续处理。
                switch (task.getTaskType()) {
                    case USERNAME -> auditHelper.markUsernameHumanReview(task.getUserId());
                    case SIGNATURE -> auditHelper.markSignatureHumanReview(task.getUserId());
                    case AVATAR -> auditHelper.markAvatarHumanReview(task.getUserId());
                }
                if (!updateTaskIfStatus(taskId, AuditTaskStatus.PROCESSING,
                        AuditTaskStatus.HUMAN_REVIEW, null, reason)) {
                    // 任务状态已被其他实例改变时释放字段占用，避免资料永远卡在人工审核中。
                    rollbackField(task.getTaskType(), task.getUserId(), payload);
                    return;
                }
                notificationProducer.publishHumanReview(task.getUserId(), task.getTaskType(), score, reason);
                UserAuditTask current = taskMapper.selectById(taskId);
                moderationTaskProducer.publishProfileAudit(taskId, task.getUserId(),
                        task.getTaskType().label(), payload.getContent(), reason,
                        current == null ? LocalDateTime.now() : current.getUpdateTime());
                return;
            }
            if (!applyPassed(task.getTaskType(), task.getUserId(), payload)) {
                rollbackField(task.getTaskType(), task.getUserId(), payload);
                updateTaskIfStatus(taskId, AuditTaskStatus.PROCESSING,
                        AuditTaskStatus.FAILED, null, "审核结果回写失败");
                notificationProducer.publishRejected(task.getUserId(), task.getTaskType(), score, "审核结果回写失败");
                return;
            }
            // 资料回写成功后才标记 PASSED，避免任务状态领先于业务数据。
            if (updateTaskIfStatus(taskId, AuditTaskStatus.PROCESSING,
                    AuditTaskStatus.PASSED, null, UserStrings.EMPTY)) {
                notificationProducer.publishPassed(task.getUserId(), task.getTaskType(), score, reason);
            } else {
                log.warn("审核通过结果落库状态失败，跳过重复通知: taskId={}", taskId);
            }
        } catch (Exception e) {
            String message = abbreviate("字段审核异常: " + e.getMessage());
            rollbackField(task.getTaskType(), task.getUserId(), payload);
            updateTaskIfStatus(taskId, AuditTaskStatus.PROCESSING,
                    AuditTaskStatus.FAILED, null, message);
            notificationProducer.publishRejected(task.getUserId(), task.getTaskType(),
                    UserConstants.AuditScore.MIN, message);
            log.warn("字段审核异常: taskId={}, type={}", taskId, task.getTaskType(), e);
        }
    }

    /** 通过数据库状态 CAS 领取 PENDING 任务，保证多实例只会有一个执行者。 */
    private boolean claimPending(Long taskId) {
        if (taskId == null) {
            return false;
        }
        return taskMapper.update(null, new LambdaUpdateWrapper<UserAuditTask>()
                .eq(UserAuditTask::getId, taskId)
                .eq(UserAuditTask::getStatus, AuditTaskStatus.PENDING)
                .set(UserAuditTask::getStatus, AuditTaskStatus.PROCESSING)
                .set(UserAuditTask::getUpdateTime, LocalDateTime.now())) == 1;
    }

    /** 人工审核通过时回写资料；头像还要先从私有对象发布为公开对象。 */
    public boolean applyPassed(AuditFieldType taskType, Long userId, FieldAuditPayload payload) {
        if (taskType == null || userId == null || payload == null) {
            return false;
        }
        return switch (taskType) {
            case USERNAME -> auditHelper.applyUsernamePassed(userId, payload.getUserVersion(), payload.getContent());
            case SIGNATURE -> auditHelper.applySignaturePassed(userId, payload.getUserVersion(), payload.getContent());
            case AVATAR -> {
                // 头像发布和资料 CAS 必须配套；CAS 失败时删除新公开对象，避免泄漏。
                String publicUrl = minIOUtils.publishPrivateAvatar(payload.getPendingObjectName());
                boolean updated = auditHelper.applyAvatarPassed(
                        userId, payload.getUserVersion(), payload.getPendingObjectName(), publicUrl);
                if (!updated) {
                    minIOUtils.deletePrivateAvatar(payload.getPendingObjectName());
                    minIOUtils.deletePublicAvatarByUrl(publicUrl);
                    yield false;
                }
                minIOUtils.deletePrivateAvatar(payload.getPendingObjectName());
                if (StringUtils.hasText(payload.getOldAvatarUrl())) {
                    try {
                        minIOUtils.deletePublicAvatarByUrl(payload.getOldAvatarUrl());
                    } catch (Exception e) {
                        log.warn("删除旧头像失败: {}", payload.getOldAvatarUrl(), e);
                    }
                }
                yield true;
            }
        };
    }

    /** 审核失败或队列拒绝时释放字段占用，并清理待审头像。 */
    public void rollbackField(AuditFieldType taskType, Long userId, FieldAuditPayload payload) {
        if (taskType == null || userId == null) {
            log.warn("审核回滚参数无效: taskType={}, userId={}", taskType, userId);
            return;
        }
        switch (taskType) {
            case USERNAME -> auditHelper.clearUsernameAudit(userId,
                    payload == null ? null : payload.getContent());
            case SIGNATURE -> auditHelper.clearSignatureAudit(userId,
                    payload == null ? null : payload.getContent());
            case AVATAR -> {
                String objectName = payload == null ? null : payload.getPendingObjectName();
                auditHelper.clearAvatarAudit(userId, objectName);
                if (StringUtils.hasText(objectName)) {
                    try {
                        minIOUtils.deletePrivateAvatar(objectName);
                    } catch (Exception e) {
                        log.warn("删除待审头像失败: object={}", objectName, e);
                    }
                }
            }
        }
    }

    /** 将 AI 返回结果归一化为分数、原因和最终分支。 */
    private AuditFieldResult result(String fieldLabel, ModerationResultVO auditResult) {
        if (auditResult == null) {
            return new AuditFieldResult(UserConstants.AuditScore.REJECT_DEFAULT,
                    fieldLabel + "审核结果为空");
        }
        String reason = StringUtils.hasText(auditResult.getReason())
                ? auditResult.getReason() : fieldLabel + "审核未通过";
        Integer score = auditResult.getScore();
        if (score == null) {
            score = auditResult.getResult() == ModerationDecision.PASS
                    ? UserConstants.AuditScore.PASS_DEFAULT : UserConstants.AuditScore.REJECT_DEFAULT;
        }
        return new AuditFieldResult(score, reason);
    }

    /** 调用 AI Agent；网络或服务异常统一转人工审核。 */
    private ModerationResultVO moderate(ModerationRequest request) {
        try {
            Result<ModerationResultVO> response = aiAgentFeignClient.moderate(request);
            if (response != null && response.getCode() != null && response.getCode() == 200
                    && response.getData() != null) {
                return response.getData();
            }
            return unavailable(request.getType());
        } catch (Exception e) {
            log.warn("AI Agent 审核服务不可用: type={}, error={}", request.getType(), e.getMessage());
            return unavailable(request.getType());
        }
    }

    /** 构造跨服务失败时的统一人工复核结果。 */
    private ModerationResultVO unavailable(ModerationContentType type) {
        ModerationResultVO result = new ModerationResultVO();
        result.setType(type);
        result.setKeywordAudit(type == ModerationContentType.TEXT
                ? ModerationCheckResult.PASS : ModerationCheckResult.NOT_APPLICABLE);
        result.setMatchedKeywords(List.of());
        result.setAiAudit(ModerationCheckResult.NOT_EXECUTED);
        result.setScore(5);
        result.setReason("AI审核服务不可用，转人工审核");
        result.setResult(ModerationDecision.HUMAN_REVIEW);
        return result;
    }

    /** 原子抢占人工复核任务，防止同一任务被同时通过和拒绝。 */
    public boolean claimHumanReview(Long taskId) {
        return updateTaskIfStatus(taskId, AuditTaskStatus.HUMAN_REVIEW,
                AuditTaskStatus.PROCESSING, null, null);
    }

    /** 仅在任务仍处于预期状态时更新，避免迟到线程覆盖最终审核结果。 */
    public boolean updateTaskIfStatus(Long taskId, AuditTaskStatus expectedStatus,
                                      AuditTaskStatus status, Integer score, String errorMessage) {
        LambdaUpdateWrapper<UserAuditTask> update = new LambdaUpdateWrapper<UserAuditTask>()
                .eq(UserAuditTask::getId, taskId)
                .eq(UserAuditTask::getStatus, expectedStatus)
                .set(UserAuditTask::getUpdateTime, LocalDateTime.now());
        if (status != null) {
            update.set(UserAuditTask::getStatus, status);
        }
        if (score != null) {
            update.set(UserAuditTask::getScore, score);
        }
        update.set(UserAuditTask::getErrorMessage, abbreviate(errorMessage));
        return taskMapper.update(null, update) == 1;
    }

    /** 将任务错误原因限制在数据库字段长度内。 */
    private String abbreviate(String message) {
        if (!StringUtils.hasText(message)) {
            return UserStrings.EMPTY;
        }
        return message.length() <= UserConstants.AuditScore.ERROR_MAX_LENGTH
                ? message : message.substring(0, UserConstants.AuditScore.ERROR_MAX_LENGTH);
    }

    private record AuditFieldResult(Integer score, String reason) {
        /** 判断审核分数是否落在人工复核区间。 */
        boolean humanReview() {
            return score != null && score > UserConstants.AuditScore.REJECT_MAX
                    && score <= UserConstants.AuditScore.HUMAN_REVIEW_MAX;
        }

        /** 判断审核分数是否应直接拒绝。 */
        boolean reject() {
            return score == null || score <= UserConstants.AuditScore.REJECT_MAX;
        }
    }
}
