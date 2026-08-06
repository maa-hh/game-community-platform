package com.game.community.content.schedule;

import com.game.community.content.service.TaskService;
import com.game.community.model.entity.task.Task;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 任务调度器
 *
 * 优化：
 * 1. 每秒钟批量获取任务并并发执行
 * 2. 每分钟更新ZSet到队列
 * 3. 每5分钟更新数据库到ZSet
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskScheduler1 {

    private final TaskService taskService;

    /**
     * 每秒钟执行一次，批量获取任务并并发执行
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
     * 每分钟执行一次，更新ZSet到队列
     */
    @Scheduled(cron = "0 * * * * ?")
    public void updateZSetToQueue() {
        try {
            taskService.updateZSetToQueue();
        } catch (Exception e) {
            log.error("更新ZSet到队列异常", e);
        }
    }

    /**
     * 每30秒执行一次，回灌数据库中尚未入 Redis 的任务
     */
    @Scheduled(fixedDelay = 30000)
    public void updateDatabaseToRedis() {
        try {
            taskService.updateDatabaseToRedis();
        } catch (Exception e) {
            log.error("回灌数据库任务到Redis异常", e);
        }
    }
}
