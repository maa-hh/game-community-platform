package com.game.community.content.schedule;

import com.game.community.content.service.TaskService;
import com.game.community.model.entity.task.Task;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 内容任务调度器：负责 Redis 队列消费、延迟任务转移和数据库补偿。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContentTaskScheduler {

    private final TaskService taskService;

    /**
     * 扫描 Redis 中的待执行任务并批量执行。
     */
    @Scheduled(fixedDelay = 1000)
    public void executePendingTasks() {
        try {
            List<Task> pendingTasks = taskService.getPendingTasks();
            if (!pendingTasks.isEmpty()) {
                taskService.executeTasks(pendingTasks);
            }
        } catch (Exception e) {
            log.error("扫描待执行任务异常", e);
        }
    }

    /**
     * 将到期的延迟任务从 Redis ZSet 转移到立即执行队列。
     */
    @Scheduled(cron = "0 * * * * ?")
    public void updateZSetToQueue() {
        try {
            taskService.updateZSetToQueue();
        } catch (Exception e) {
            log.error("更新 ZSet 到队列异常", e);
        }
    }

    /**
     * 将数据库中尚未入 Redis 的任务回灌到队列。
     */
    @Scheduled(fixedDelay = 30000)
    public void updateDatabaseToRedis() {
        try {
            taskService.updateDatabaseToRedis();
        } catch (Exception e) {
            log.error("回灌数据库任务到 Redis 异常", e);
        }
    }
}
