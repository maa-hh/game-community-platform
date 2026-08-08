package com.game.community.user.runner;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.game.community.common.constant.user.UserConstants;
import com.game.community.model.entity.user.UserAuditTask;
import com.game.community.model.enums.user.AuditFieldType;
import com.game.community.model.enums.user.AuditTaskStatus;
import com.game.community.user.event.executor.AuditTaskExecutor;
import com.game.community.user.mapper.UserAuditTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 审核任务恢复器：服务启动时分批重新提交未完成的审核任务。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserAuditTaskRecoveryRunner implements ApplicationRunner {

    private static final long INITIAL_LAST_ID = 0L;
    private static final String RECOVERY_SOURCE = "startup-recovery";

    private static final Set<AuditFieldType> SUPPORTED = Set.of(
            AuditFieldType.USERNAME,
            AuditFieldType.SIGNATURE,
            AuditFieldType.AVATAR
    );

    private final UserAuditTaskMapper userAuditTaskMapper;
    private final AuditTaskExecutor auditTaskExecutor;

    @Override
    public void run(ApplicationArguments args) {
        int staleCount = resetStaleTasks();
        int pendingCount = enqueuePendingTasks();
        if (pendingCount > 0 || staleCount > 0) {
            log.info("审核任务恢复完成: pending={}, stale={}", pendingCount, staleCount);
        }
    }

    private int resetStaleTasks() {
        int count = 0;
        long lastId = INITIAL_LAST_ID;
        LocalDateTime staleBefore = LocalDateTime.now().minusMinutes(UserConstants.AUDIT_TASK_STALE_MINUTES);
        while (true) {
            List<UserAuditTask> tasks = selectBatch(AuditTaskStatus.PROCESSING, lastId, staleBefore);
            if (tasks.isEmpty()) {
                return count;
            }
            for (UserAuditTask task : tasks) {
                int updated = userAuditTaskMapper.update(null, new LambdaUpdateWrapper<UserAuditTask>()
                        .eq(UserAuditTask::getId, task.getId())
                        .eq(UserAuditTask::getStatus, AuditTaskStatus.PROCESSING)
                        .set(UserAuditTask::getStatus, AuditTaskStatus.PENDING)
                        .set(UserAuditTask::getUpdateTime, LocalDateTime.now()));
                if (updated > 0) {
                    count++;
                    log.info("重置超时审核任务: taskId={}, type={}", task.getId(), task.getTaskType());
                }
                lastId = task.getId();
            }
        }
    }

    private int enqueuePendingTasks() {
        int count = 0;
        long lastId = INITIAL_LAST_ID;
        while (true) {
            List<UserAuditTask> tasks = selectBatch(AuditTaskStatus.PENDING, lastId, null);
            if (tasks.isEmpty()) {
                return count;
            }
            for (UserAuditTask task : tasks) {
                if (SUPPORTED.contains(task.getTaskType())) {
                    try {
                        auditTaskExecutor.submit(task.getId());
                        count++;
                    } catch (java.util.concurrent.RejectedExecutionException ex) {
                        Map<String, Object> snapshot = new LinkedHashMap<>();
                        snapshot.put("source", RECOVERY_SOURCE);
                        snapshot.put("taskId", task.getId());
                        snapshot.put("pendingContent", task.getPendingContent());
                        snapshot.put("payload", task.getPayload());
                        auditTaskExecutor.handleRejected(
                                task.getId(),
                                task.getTaskType(),
                                task.getUserId(),
                                snapshot,
                                ex);
                        log.warn("启动恢复入队失败(服务繁忙): taskId={}, type={}", task.getId(), task.getTaskType());
                    }
                } else {
                    log.warn("未知或已废弃审核任务类型，跳过恢复: taskId={}, taskType={}",
                            task.getId(), task.getTaskType());
                }
                lastId = task.getId();
            }
        }
    }

    private List<UserAuditTask> selectBatch(AuditTaskStatus status, long lastId, LocalDateTime updateBefore) {
        LambdaQueryWrapper<UserAuditTask> wrapper = new LambdaQueryWrapper<UserAuditTask>()
                .eq(UserAuditTask::getStatus, status)
                .gt(lastId > 0, UserAuditTask::getId, lastId)
                .lt(updateBefore != null, UserAuditTask::getUpdateTime, updateBefore)
                .orderByAsc(UserAuditTask::getId)
                .last("LIMIT " + UserConstants.AUDIT_RECOVERY_BATCH_SIZE);
        return userAuditTaskMapper.selectList(wrapper);
    }
}
