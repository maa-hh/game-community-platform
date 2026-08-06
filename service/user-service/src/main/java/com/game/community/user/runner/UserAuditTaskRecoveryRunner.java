package com.game.community.user.runner;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.game.community.common.constant.user.UserConstants;
import com.game.community.model.entity.user.UserAuditTask;
import com.game.community.model.enums.user.AuditFieldType;
import com.game.community.model.enums.user.AuditTaskStatus;
import com.game.community.user.mapper.UserAuditTaskMapper;
import com.game.community.user.service.UserFieldAuditTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 审核任务恢复器：服务启动时重新提交未完成的审核任务。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserAuditTaskRecoveryRunner implements ApplicationRunner {

    private static final Set<AuditFieldType> SUPPORTED = Set.of(
            AuditFieldType.USERNAME,
            AuditFieldType.SIGNATURE,
            AuditFieldType.AVATAR
    );

    private final UserAuditTaskMapper userAuditTaskMapper;
    private final UserFieldAuditTaskService fieldAuditTaskService;

    @Override
    public void run(ApplicationArguments args) {
        List<UserAuditTask> pendingTasks = userAuditTaskMapper.selectList(new LambdaQueryWrapper<UserAuditTask>()
                .eq(UserAuditTask::getStatus, AuditTaskStatus.PENDING)
                .orderByAsc(UserAuditTask::getId));

        List<UserAuditTask> staleTasks = userAuditTaskMapper.selectList(new LambdaQueryWrapper<UserAuditTask>()
                .eq(UserAuditTask::getStatus, AuditTaskStatus.PROCESSING)
                .lt(UserAuditTask::getUpdateTime, LocalDateTime.now().minusMinutes(UserConstants.AUDIT_TASK_STALE_MINUTES))
                .orderByAsc(UserAuditTask::getId));

        for (UserAuditTask task : staleTasks) {
            userAuditTaskMapper.update(null, new LambdaUpdateWrapper<UserAuditTask>()
                    .eq(UserAuditTask::getId, task.getId())
                    .eq(UserAuditTask::getStatus, AuditTaskStatus.PROCESSING)
                    .set(UserAuditTask::getStatus, AuditTaskStatus.PENDING)
                    .set(UserAuditTask::getUpdateTime, LocalDateTime.now()));
            log.info("重置超时审核任务: taskId={}, type={}", task.getId(), task.getTaskType());
        }

        List<UserAuditTask> allTasks = userAuditTaskMapper.selectList(new LambdaQueryWrapper<UserAuditTask>()
                .eq(UserAuditTask::getStatus, AuditTaskStatus.PENDING)
                .orderByAsc(UserAuditTask::getId));

        for (UserAuditTask task : allTasks) {
            if (SUPPORTED.contains(task.getTaskType())) {
                try {
                    fieldAuditTaskService.enqueueFieldAudit(task.getId());
                } catch (java.util.concurrent.RejectedExecutionException ex) {
                    fieldAuditTaskService.handleEnqueueRejected(
                            task.getId(),
                            task.getTaskType(),
                            task.getUserId(),
                            Map.of(
                                    "source", "startup-recovery",
                                    "taskId", task.getId(),
                                    "pendingContent", task.getPendingContent(),
                                    "payload", task.getPayload()
                            ),
                            ex);
                    log.warn("启动恢复入队失败(服务繁忙): taskId={}, type={}", task.getId(), task.getTaskType());
                }
            } else {
                log.warn("未知或已废弃审核任务类型，跳过恢复: taskId={}, taskType={}",
                        task.getId(), task.getTaskType());
            }
        }

        if (!pendingTasks.isEmpty() || !staleTasks.isEmpty()) {
            log.info("审核任务恢复完成: pending={}, stale={}", pendingTasks.size(), staleTasks.size());
        }
    }
}
