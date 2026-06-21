package com.game.community.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.community.common.constant.user.UserConstants;
import com.game.community.model.entity.user.User;
import com.game.community.model.entity.user.UserAuditTask;
import com.game.community.model.payload.user.ProfileAuditPayload;
import com.game.community.user.mapper.UserAuditTaskMapper;
import com.game.community.user.mapper.UserMapper;
import com.game.community.user.service.UserAuditService;
import com.game.community.user.service.UserProfileAuditTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserProfileAuditTaskServiceImpl implements UserProfileAuditTaskService {

    private final UserMapper userMapper;

    private final UserAuditTaskMapper userAuditTaskMapper;

    private final UserAuditService userAuditService;

    private final ObjectMapper objectMapper;

    @Async("auditExecutor")
    @Override
    public void auditProfileAsync(Long taskId) {
        UserAuditTask task = userAuditTaskMapper.selectById(taskId);
        if (task == null || !UserConstants.AuditTaskStatus.PENDING.equals(task.getStatus())) {
            return;
        }
        if (!claimTask(taskId)) {
            return;
        }

        try {
            ProfileAuditPayload payload = objectMapper.readValue(task.getPayload(), ProfileAuditPayload.class);
            User currentUser = userMapper.selectById(task.getUserId());
            if (currentUser == null || currentUser.getStatus() == UserConstants.UserStatus.DISABLED) {
                failTask(taskId, task.getUserId(), "用户已失效，无法回写审核结果", new IllegalStateException("user is disabled"));
                return;
            }
            if (StringUtils.hasText(payload.getPhone())) {
                Long duplicatePhoneCount = userMapper.selectCount(new LambdaQueryWrapper<User>()
                        .eq(User::getPhone, payload.getPhone())
                        .ne(User::getId, task.getUserId()));
                if (duplicatePhoneCount != null && duplicatePhoneCount > 0) {
                    rejectTask(taskId, task.getUserId(), "手机号已被其他用户使用");
                    return;
                }
            }
            boolean passed = userAuditService.auditUserInfo(payload.getUsername(), payload.getSignature());
            if (!passed) {
                rejectTask(taskId, task.getUserId(), "用户资料审核未通过");
                return;
            }

            LambdaUpdateWrapper<User> userUpdate = new LambdaUpdateWrapper<User>()
                    .eq(User::getId, task.getUserId())
                    .eq(User::getVersion, payload.getUserVersion())
                    .eq(User::getAuditStatus, UserConstants.AuditStatus.AUDITING)
                    .set(User::getAuditStatus, UserConstants.AuditStatus.NONE)
                    .set(User::getVersion, payload.getUserVersion() + 1)
                    .set(User::getUpdateTime, LocalDateTime.now());
            if (payload.getUsername() != null) {
                userUpdate.set(User::getUsername, payload.getUsername());
            }
            if (payload.getSignature() != null) {
                userUpdate.set(User::getSignature, payload.getSignature());
            }
            if (payload.getPhone() != null) {
                userUpdate.set(User::getPhone, payload.getPhone());
            }
            if (payload.getGameAccount() != null) {
                userUpdate.set(User::getGameAccount, payload.getGameAccount());
            }
            int updated = userMapper.update(null, userUpdate);
            if (updated <= 0) {
                failTask(taskId, task.getUserId(), "用户资料审核结果回写失败", new IllegalStateException("auditStatus is not AUDITING"));
                return;
            }

            userAuditTaskMapper.update(null, new LambdaUpdateWrapper<UserAuditTask>()
                    .eq(UserAuditTask::getId, taskId)
                    .eq(UserAuditTask::getStatus, UserConstants.AuditTaskStatus.PROCESSING)
                    .set(UserAuditTask::getStatus, UserConstants.AuditTaskStatus.PASSED)
                    .set(UserAuditTask::getErrorMessage, null)
                    .set(UserAuditTask::getUpdateTime, LocalDateTime.now()));
            log.info("用户资料异步审核通过: taskId={}, userId={}", taskId, task.getUserId());
        } catch (Exception e) {
            failTask(taskId, task.getUserId(), "用户资料审核异常", e);
        }
    }

    private void rejectTask(Long taskId, Long userId, String reason) {
        clearAuditStatus(userId, taskId);
        userAuditTaskMapper.update(null, new LambdaUpdateWrapper<UserAuditTask>()
                .eq(UserAuditTask::getId, taskId)
                .eq(UserAuditTask::getStatus, UserConstants.AuditTaskStatus.PROCESSING)
                .set(UserAuditTask::getStatus, UserConstants.AuditTaskStatus.REJECTED)
                .set(UserAuditTask::getErrorMessage, reason)
                .set(UserAuditTask::getUpdateTime, LocalDateTime.now()));
        log.warn("用户资料异步审核未通过: taskId={}, userId={}, reason={}", taskId, userId, reason);
    }

    private boolean claimTask(Long taskId) {
        return userAuditTaskMapper.update(null, new LambdaUpdateWrapper<UserAuditTask>()
                .eq(UserAuditTask::getId, taskId)
                .eq(UserAuditTask::getStatus, UserConstants.AuditTaskStatus.PENDING)
                .set(UserAuditTask::getStatus, UserConstants.AuditTaskStatus.PROCESSING)
                .set(UserAuditTask::getUpdateTime, LocalDateTime.now())) > 0;
    }

    private void failTask(Long taskId, Long userId, String message, Exception e) {
        clearAuditStatus(userId, taskId);
        userAuditTaskMapper.update(null, new LambdaUpdateWrapper<UserAuditTask>()
                .eq(UserAuditTask::getId, taskId)
                .eq(UserAuditTask::getStatus, UserConstants.AuditTaskStatus.PROCESSING)
                .set(UserAuditTask::getStatus, UserConstants.AuditTaskStatus.FAILED)
                .set(UserAuditTask::getErrorMessage, abbreviate(message + ": " + e.getMessage()))
                .set(UserAuditTask::getUpdateTime, LocalDateTime.now()));
        log.warn("用户资料异步审核异常: taskId={}, userId={}, error={}", taskId, userId, e.getMessage(), e);
    }

    private void clearAuditStatus(Long userId, Long taskId) {
        ProfileAuditPayload payload = readPayload(taskId);
        if (payload == null || payload.getUserVersion() == null) {
            return;
        }
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .eq(User::getVersion, payload.getUserVersion())
                .set(User::getAuditStatus, UserConstants.AuditStatus.NONE)
                .set(User::getVersion, payload.getUserVersion() + 1)
                .set(User::getUpdateTime, LocalDateTime.now()));
    }

    private ProfileAuditPayload readPayload(Long taskId) {
        try {
            UserAuditTask task = userAuditTaskMapper.selectById(taskId);
            if (task == null) {
                return null;
            }
            return objectMapper.readValue(task.getPayload(), ProfileAuditPayload.class);
        } catch (Exception e) {
            log.warn("读取资料审核任务负载失败: taskId={}, error={}", taskId, e.getMessage());
            return null;
        }
    }

    private String abbreviate(String message) {
        if (!StringUtils.hasText(message) || message.length() <= 255) {
            return message;
        }
        return message.substring(0, 255);
    }
}
