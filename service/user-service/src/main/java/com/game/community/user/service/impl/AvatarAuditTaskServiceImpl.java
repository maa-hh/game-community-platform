package com.game.community.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.community.common.constant.user.UserConstants;
import com.game.community.model.entity.user.User;
import com.game.community.model.entity.user.UserAuditTask;
import com.game.community.model.payload.user.AvatarAuditPayload;
import com.game.community.user.mapper.UserAuditTaskMapper;
import com.game.community.user.mapper.UserMapper;
import com.game.community.user.service.AvatarAuditTaskService;
import com.game.community.user.service.UserAuditService;
import com.game.community.utils.MinIOUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AvatarAuditTaskServiceImpl implements AvatarAuditTaskService {

    private final UserMapper userMapper;

    private final UserAuditTaskMapper userAuditTaskMapper;

    private final UserAuditService userAuditService;

    private final MinIOUtils minIOUtils;

    private final ObjectMapper objectMapper;

    @Async("auditExecutor")
    @Override
    public void auditAvatarAsync(Long taskId) {
        UserAuditTask task = userAuditTaskMapper.selectById(taskId);
        if (task == null || !UserConstants.AuditTaskStatus.PENDING.equals(task.getStatus())) {
            return;
        }
        if (!claimTask(taskId)) {
            return;
        }
        try {
            AvatarAuditPayload payload = objectMapper.readValue(task.getPayload(), AvatarAuditPayload.class);
            User currentUser = userMapper.selectById(task.getUserId());
            if (currentUser == null || currentUser.getStatus() == UserConstants.UserStatus.DISABLED) {
                clearAuditStatus(task.getUserId(), payload.getUserVersion());
                completeTask(taskId, UserConstants.AuditTaskStatus.FAILED, "用户已失效，无法回写头像");
                minIOUtils.deletePrivateAvatar(payload.getPendingObjectName());
                return;
            }
            String auditAvatarUrl = minIOUtils.generatePrivateAvatarUrl(payload.getPendingObjectName());
            boolean passed = userAuditService.auditAvatarUrl(auditAvatarUrl);
            if (passed) {
                String publicAvatarUrl = minIOUtils.publishPrivateAvatar(payload.getPendingObjectName());
                int updated = userMapper.update(null, new LambdaUpdateWrapper<User>()
                        .eq(User::getId, task.getUserId())
                        .eq(User::getVersion, payload.getUserVersion())
                        .eq(User::getAuditStatus, UserConstants.AuditStatus.AUDITING)
                        .set(User::getAvatar, publicAvatarUrl)
                        .set(User::getAuditStatus, UserConstants.AuditStatus.NONE)
                        .set(User::getVersion, payload.getUserVersion() + 1)
                        .set(User::getUpdateTime, LocalDateTime.now()));
                if (updated <= 0) {
                    minIOUtils.deletePrivateAvatar(payload.getPendingObjectName());
                    minIOUtils.deletePublicAvatarByUrl(publicAvatarUrl);
                    completeTask(taskId, UserConstants.AuditTaskStatus.FAILED, "头像审核结果回写失败");
                    return;
                }
                minIOUtils.deletePrivateAvatar(payload.getPendingObjectName());
                deleteQuietly(payload.getOldAvatarUrl(), "旧头像");
                completeTask(taskId, UserConstants.AuditTaskStatus.PASSED, null);
                log.info("头像异步审核通过: taskId={}, userId={}, url={}", taskId, task.getUserId(), publicAvatarUrl);
            } else {
                clearAuditStatus(task.getUserId(), payload.getUserVersion());
                minIOUtils.deletePrivateAvatar(payload.getPendingObjectName());
                completeTask(taskId, UserConstants.AuditTaskStatus.REJECTED, "头像审核未通过");
                log.warn("头像异步审核未通过: taskId={}, userId={}", taskId, task.getUserId());
            }
        } catch (Exception e) {
            clearAuditStatus(task.getUserId(), readUserVersion(taskId));
            completeTask(taskId, UserConstants.AuditTaskStatus.FAILED, abbreviate("头像审核异常: " + e.getMessage()));
            log.warn("头像异步审核异常: taskId={}, userId={}, error={}", taskId, task.getUserId(), e.getMessage(), e);
        }
    }

    private void clearAuditStatus(Long userId, Integer version) {
        if (version == null) {
            return;
        }
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .eq(User::getVersion, version)
                .eq(User::getAuditStatus, UserConstants.AuditStatus.AUDITING)
                .set(User::getAuditStatus, UserConstants.AuditStatus.NONE)
                .set(User::getVersion, version + 1)
                .set(User::getUpdateTime, LocalDateTime.now()));
    }

    private boolean claimTask(Long taskId) {
        return userAuditTaskMapper.update(null, new LambdaUpdateWrapper<UserAuditTask>()
                .eq(UserAuditTask::getId, taskId)
                .eq(UserAuditTask::getStatus, UserConstants.AuditTaskStatus.PENDING)
                .set(UserAuditTask::getStatus, UserConstants.AuditTaskStatus.PROCESSING)
                .set(UserAuditTask::getUpdateTime, LocalDateTime.now())) > 0;
    }

    private void completeTask(Long taskId, String status, String errorMessage) {
        userAuditTaskMapper.update(null, new LambdaUpdateWrapper<UserAuditTask>()
                .eq(UserAuditTask::getId, taskId)
                .eq(UserAuditTask::getStatus, UserConstants.AuditTaskStatus.PROCESSING)
                .set(UserAuditTask::getStatus, status)
                .set(UserAuditTask::getErrorMessage, errorMessage)
                .set(UserAuditTask::getUpdateTime, LocalDateTime.now()));
    }

    private void deleteQuietly(String fileUrl, String description) {
        if (!StringUtils.hasText(fileUrl)) {
            return;
        }
        try {
            minIOUtils.deletePublicAvatarByUrl(fileUrl);
        } catch (Exception e) {
            log.warn("删除{}失败: {}", description, fileUrl, e);
        }
    }

    private String abbreviate(String message) {
        if (!StringUtils.hasText(message) || message.length() <= 255) {
            return message;
        }
        return message.substring(0, 255);
    }

    private Integer readUserVersion(Long taskId) {
        try {
            UserAuditTask task = userAuditTaskMapper.selectById(taskId);
            if (task == null) {
                return null;
            }
            AvatarAuditPayload payload = objectMapper.readValue(task.getPayload(), AvatarAuditPayload.class);
            return payload.getUserVersion();
        } catch (Exception e) {
            log.warn("读取头像审核任务负载失败: taskId={}, error={}", taskId, e.getMessage());
            return null;
        }
    }
}
