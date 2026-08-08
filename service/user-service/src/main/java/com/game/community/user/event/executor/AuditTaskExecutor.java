package com.game.community.user.event.executor;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.community.common.constant.user.UserConstants;
import com.game.community.model.entity.user.User;
import com.game.community.model.entity.user.UserAuditRejectLog;
import com.game.community.model.entity.user.UserAuditTask;
import com.game.community.model.enums.user.AuditFieldType;
import com.game.community.model.enums.user.AuditTaskStatus;
import com.game.community.model.enums.user.UserStrings;
import com.game.community.model.payload.user.FieldAuditPayload;
import com.game.community.user.common.audit.UserAuditHelper;
import com.game.community.user.event.kafka.ModerationTaskProducer;
import com.game.community.user.event.kafka.ProfileAuditNotificationProducer;
import com.game.community.user.mapper.UserAuditRejectLogMapper;
import com.game.community.user.mapper.UserAuditTaskMapper;
import com.game.community.user.mapper.UserMapper;
import com.game.community.utils.DfaAuditUtils;
import com.game.community.utils.MinIOUtils;
import com.game.community.utils.audit.AuditClient;
import com.game.community.utils.audit.AuditResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
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
    private final DfaAuditUtils dfaAuditUtils;
    private final AuditClient auditClient;
    private final MinIOUtils minIOUtils;
    private final ProfileAuditNotificationProducer notificationProducer;
    private final ModerationTaskProducer moderationTaskProducer;
    private final ObjectMapper objectMapper;

    public AuditTaskExecutor(@Qualifier("auditExecutor") Executor auditExecutor,
                             UserAuditTaskMapper taskMapper,
                             UserAuditRejectLogMapper rejectLogMapper,
                             UserMapper userMapper,
                             UserAuditHelper auditHelper,
                             DfaAuditUtils dfaAuditUtils,
                             AuditClient auditClient,
                             MinIOUtils minIOUtils,
                             ProfileAuditNotificationProducer notificationProducer,
                             ModerationTaskProducer moderationTaskProducer,
                             ObjectMapper objectMapper) {
        this.auditExecutor = auditExecutor;
        this.taskMapper = taskMapper;
        this.rejectLogMapper = rejectLogMapper;
        this.userMapper = userMapper;
        this.auditHelper = auditHelper;
        this.dfaAuditUtils = dfaAuditUtils;
        this.auditClient = auditClient;
        this.minIOUtils = minIOUtils;
        this.notificationProducer = notificationProducer;
        this.moderationTaskProducer = moderationTaskProducer;
        this.objectMapper = objectMapper;
    }

    /** 任务已在事务中落库；这里只负责提交到审核线程池。 */
    public void submit(Long taskId) {
        auditExecutor.execute(() -> run(taskId));
    }

    /** 队列满时释放字段占用、结束任务，并保留拒绝请求供排查。 */
    public void handleRejected(Long taskId, AuditFieldType taskType, Long userId,
                               Object requestPayload, RejectedExecutionException cause) {
        UserAuditTask task = taskId == null ? null : taskMapper.selectById(taskId);
        FieldAuditPayload payload = task == null ? null : auditHelper.readPayload(task);
        if (task != null) {
            rollbackField(task.getTaskType(), task.getUserId(), payload);
            updateTask(task.getId(), AuditTaskStatus.FAILED, null, "审核服务繁忙，队列已满");
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

    /** 抢占 PENDING 任务后完成审核，分数决定通过、人工复核或拒绝。 */
    public void run(Long taskId) {
        UserAuditTask task = taskMapper.selectById(taskId);
        if (task == null || task.getStatus() != AuditTaskStatus.PENDING
                || taskMapper.update(null, new LambdaUpdateWrapper<UserAuditTask>()
                .eq(UserAuditTask::getId, taskId)
                .eq(UserAuditTask::getStatus, AuditTaskStatus.PENDING)
                .set(UserAuditTask::getStatus, AuditTaskStatus.PROCESSING)
                .set(UserAuditTask::getUpdateTime, LocalDateTime.now())) == 0) {
            return;
        }

        FieldAuditPayload payload = auditHelper.readPayload(task);
        if (payload == null || payload.getField() == null) {
            rollbackField(task.getTaskType(), task.getUserId(), payload);
            updateTask(taskId, AuditTaskStatus.FAILED, null, "审核任务负载异常");
            notificationProducer.publishRejected(task.getUserId(), task.getTaskType(),
                    UserConstants.AuditScore.MIN, "审核任务负载异常");
            return;
        }

        try {
            AuditFieldResult result;
            if (task.getTaskType() == AuditFieldType.AVATAR) {
                String url = minIOUtils.generatePrivateAvatarUrl(payload.getPendingObjectName());
                try {
                    MinIOUtils.FilePayload file = minIOUtils.readFileByUrl(url);
                    result = result("头像", auditClient.auditImage(file.bytes(), file.contentType()));
                } catch (Exception e) {
                    log.warn("头像图片读取失败，回退为 URL 审核: url={}", url, e);
                    result = result("头像", auditClient.auditImageUrl(url));
                }
            } else if (!StringUtils.hasText(payload.getContent())) {
                result = new AuditFieldResult(UserConstants.AuditScore.MAX, "空内容");
            } else if (!dfaAuditUtils.pass(payload.getContent())) {
                log.warn("{}本地敏感词未通过: {}", task.getTaskType().label(), payload.getContent());
                result = new AuditFieldResult(UserConstants.AuditScore.REJECT_DEFAULT,
                        task.getTaskType().label() + "包含敏感词");
            } else {
                result = result(task.getTaskType().label(), auditClient.auditText(payload.getContent()));
            }

            Integer score = result.score();
            String reason = result.reason();
            updateTask(taskId, AuditTaskStatus.PROCESSING, score, reason);
            if (result.reject()) {
                rollbackField(task.getTaskType(), task.getUserId(), payload);
                updateTask(taskId, AuditTaskStatus.REJECTED, null, reason);
                notificationProducer.publishRejected(task.getUserId(), task.getTaskType(), score, reason);
                return;
            }
            if (result.humanReview()) {
                switch (task.getTaskType()) {
                    case USERNAME -> auditHelper.markUsernameHumanReview(task.getUserId());
                    case SIGNATURE -> auditHelper.markSignatureHumanReview(task.getUserId());
                    case AVATAR -> auditHelper.markAvatarHumanReview(task.getUserId());
                }
                updateTask(taskId, AuditTaskStatus.HUMAN_REVIEW, null, reason);
                notificationProducer.publishHumanReview(task.getUserId(), task.getTaskType(), score, reason);
                UserAuditTask current = taskMapper.selectById(taskId);
                moderationTaskProducer.publishProfileAudit(taskId, task.getUserId(),
                        task.getTaskType().label(), payload.getContent(), reason,
                        current == null ? LocalDateTime.now() : current.getUpdateTime());
                return;
            }
            if (!applyPassed(task.getTaskType(), task.getUserId(), payload)) {
                rollbackField(task.getTaskType(), task.getUserId(), payload);
                updateTask(taskId, AuditTaskStatus.FAILED, null, "审核结果回写失败");
                notificationProducer.publishRejected(task.getUserId(), task.getTaskType(), score, "审核结果回写失败");
                return;
            }
            updateTask(taskId, AuditTaskStatus.PASSED, null, UserStrings.EMPTY);
            notificationProducer.publishPassed(task.getUserId(), task.getTaskType(), score, reason);
        } catch (Exception e) {
            String message = abbreviate("字段审核异常: " + e.getMessage());
            rollbackField(task.getTaskType(), task.getUserId(), payload);
            updateTask(taskId, AuditTaskStatus.FAILED, null, message);
            notificationProducer.publishRejected(task.getUserId(), task.getTaskType(),
                    UserConstants.AuditScore.MIN, message);
            log.warn("字段审核异常: taskId={}, type={}", taskId, task.getTaskType(), e);
        }
    }

    /** 人工审核通过时回写资料；头像还要先从私有对象发布为公开对象。 */
    public boolean applyPassed(AuditFieldType taskType, Long userId, FieldAuditPayload payload) {
        return switch (taskType) {
            case USERNAME -> auditHelper.applyUsernamePassed(userId, payload.getUserVersion(), payload.getContent());
            case SIGNATURE -> auditHelper.applySignaturePassed(userId, payload.getUserVersion(), payload.getContent());
            case AVATAR -> {
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

    private AuditFieldResult result(String fieldLabel, AuditResult auditResult) {
        if (auditResult == null) {
            return new AuditFieldResult(UserConstants.AuditScore.REJECT_DEFAULT,
                    fieldLabel + "审核结果为空");
        }
        String reason = StringUtils.hasText(auditResult.getReason())
                ? auditResult.getReason() : fieldLabel + "审核未通过";
        Integer score = auditResult.getScore();
        if (score == null) {
            score = auditResult.isPass() ? UserConstants.AuditScore.PASS_DEFAULT
                    : UserConstants.AuditScore.REJECT_DEFAULT;
        }
        return new AuditFieldResult(score, reason);
    }

    public void updateTask(Long taskId, AuditTaskStatus status, Integer score, String errorMessage) {
        LambdaUpdateWrapper<UserAuditTask> update = new LambdaUpdateWrapper<UserAuditTask>()
                .eq(UserAuditTask::getId, taskId)
                .set(UserAuditTask::getUpdateTime, LocalDateTime.now());
        if (status != null) {
            update.set(UserAuditTask::getStatus, status);
        }
        if (score != null) {
            update.set(UserAuditTask::getScore, score);
        }
        update.set(UserAuditTask::getErrorMessage, abbreviate(errorMessage));
        taskMapper.update(null, update);
    }

    private String abbreviate(String message) {
        if (!StringUtils.hasText(message)) {
            return UserStrings.EMPTY;
        }
        return message.length() <= UserConstants.AuditScore.ERROR_MAX_LENGTH
                ? message : message.substring(0, UserConstants.AuditScore.ERROR_MAX_LENGTH);
    }

    private record AuditFieldResult(Integer score, String reason) {
        boolean humanReview() {
            return score != null && score > UserConstants.AuditScore.REJECT_MAX
                    && score <= UserConstants.AuditScore.HUMAN_REVIEW_MAX;
        }

        boolean reject() {
            return score == null || score <= UserConstants.AuditScore.REJECT_MAX;
        }
    }
}
