package com.game.community.content.service;

import com.game.community.model.entity.task.Task;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务服务接口
 */
public interface TaskService {

    /**
     * 添加立即执行的任务
     *
     * @param type       任务类型
     * @param param      任务参数
     * @param businessId 业务ID
     * @param <T>       参数类型
     * @return 任务ID
     */
    <T> Long addImmediateTask(int type, T param, Long businessId);

    /**
     * 添加延迟执行的任务
     *
     * @param type        任务类型
     * @param param       任务参数
     * @param businessId  业务ID
     * @param executeTime 执行时间
     * @param <T>         参数类型
     * @return 任务ID
     */
    <T> Long addDelayTask(int type, T param, Long businessId, LocalDateTime executeTime);

    /**
     * 取消任务
     *
     * @param taskId 任务ID
     */
    void cancelTask(Long taskId);

    /**
     * 执行任务（供定时器调用）
     *
     * @param task 任务对象
     */
    void executeTask(Task task);

    /**
     * 获取待执行的任务列表（从Redis）
     *
     * @return 任务列表
     */
    List<Task> getPendingTasks();

    /**
     * 批量并发执行任务
     *
     * @param tasks 任务列表
     */
    void executeTasks(List<Task> tasks);

    /**
     * 启动补偿：把数据库里未入队的任务重新回灌到 Redis。
     */
    void recoverPendingTasks();
}
