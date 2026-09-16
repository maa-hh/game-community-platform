package com.game.community.user.runner;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.game.community.common.constant.user.UserConstants;
import com.game.community.model.entity.user.UserAuditTask;
import com.game.community.model.enums.user.AuditTaskStatus;
import com.game.community.model.enums.user.UserStrings;
import com.game.community.user.event.executor.AuditTaskExecutor;
import com.game.community.user.mapper.UserAuditTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 审核任务恢复器：周期分批重新提交未完成的审核任务，支持多实例故障接管。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserAuditTaskRecoveryRunner {

    private final UserAuditTaskMapper userAuditTaskMapper;
    private final AuditTaskExecutor auditTaskExecutor;

    /**
     * 周期恢复未完成审核任务，使任一实例故障后留下的任务可由其他实例接管。
     * 每个实例都可以扫描，真正执行前由任务状态 CAS 选出唯一持有者。
     */
    @Scheduled(initialDelayString = "${audit.recovery.initial-delay-ms:10000}",
            fixedDelayString = "${audit.recovery.fixed-delay-ms:30000}")
    public void recover() {
        // 数据库任务表是持久队列；定期扫描填补“事务提交后、提交线程池前”实例宕机的窗口。
        int staleCount = resetStaleTasks();
        int pendingCount = enqueuePendingTasks();
        if (pendingCount > 0 || staleCount > 0) {
            log.info("审核任务恢复完成: pending={}, stale={}", pendingCount, staleCount);
        }
    }

    /** 批量把超时 PROCESSING 任务重置为可重新领取的 PENDING。 */
    private int resetStaleTasks() {
        int count = 0;
        long lastId = UserConstants.AUDIT_RECOVERY_INITIAL_LAST_ID;
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

    /** 分批提交待处理任务；多个实例由任务 CAS 选出唯一执行者。 */
    private int enqueuePendingTasks() {
        int count = 0;
        long lastId = UserConstants.AUDIT_RECOVERY_INITIAL_LAST_ID;
        while (true) {
            List<UserAuditTask> tasks = selectBatch(AuditTaskStatus.PENDING, lastId, null);
            if (tasks.isEmpty()) {
                return count;
            }
            for (UserAuditTask task : tasks) {
                try {
                    // 即使任务类型异常也交给执行器，由执行器统一标记 FAILED，避免坏任务永久占用 PENDING。
                    if (auditTaskExecutor.submit(task.getId())) {
                        count++;
                    }
                } catch (java.util.concurrent.RejectedExecutionException ex) {
                    Map<String, Object> snapshot = new LinkedHashMap<>();
                    snapshot.put("source", UserStrings.AUDIT_RECOVERY_SOURCE);
                    snapshot.put("taskId", task.getId());
                    snapshot.put("pendingContent", task.getPendingContent());
                    snapshot.put("payload", task.getPayload());
                    auditTaskExecutor.handleRejected(
                            task.getId(),
                            task.getTaskType(),
                            task.getUserId(),
                            snapshot,
                            ex);
                    log.warn("定时恢复入队失败(服务繁忙): taskId={}, type={}", task.getId(), task.getTaskType());
                }
                lastId = task.getId();
            }
        }
    }

    /** 按主键游标分页读取指定状态的审核任务，避免一次加载全表。 */
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
