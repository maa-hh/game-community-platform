package com.game.community.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.game.community.common.constant.user.UserConstants;
import com.game.community.model.entity.user.UserAuditTask;
import com.game.community.user.mapper.UserAuditTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Resubmits pending audit tasks left behind by transaction timing or service restarts.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserAuditTaskRecoveryRunner implements ApplicationRunner {

    private final UserAuditTaskMapper userAuditTaskMapper;

    private final AvatarAuditTaskService avatarAuditTaskService;

    private final UserProfileAuditTaskService userProfileAuditTaskService;

    @Override
    public void run(ApplicationArguments args) {
        List<UserAuditTask> tasks = userAuditTaskMapper.selectList(new LambdaQueryWrapper<UserAuditTask>()
                .eq(UserAuditTask::getStatus, UserConstants.AuditTaskStatus.PENDING)
                .orderByAsc(UserAuditTask::getId));
        for (UserAuditTask task : tasks) {
            if (UserConstants.AuditTaskType.AVATAR.equals(task.getTaskType())) {
                avatarAuditTaskService.auditAvatarAsync(task.getId());
            } else if (UserConstants.AuditTaskType.PROFILE.equals(task.getTaskType())) {
                userProfileAuditTaskService.auditProfileAsync(task.getId());
            } else {
                log.warn("未知审核任务类型，跳过恢复: taskId={}, taskType={}", task.getId(), task.getTaskType());
            }
        }
        if (!tasks.isEmpty()) {
            log.info("已重新提交待处理用户审核任务: count={}", tasks.size());
        }
    }
}
