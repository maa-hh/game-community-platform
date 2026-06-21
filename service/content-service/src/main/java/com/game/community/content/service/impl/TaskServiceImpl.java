package com.game.community.content.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.game.community.common.constant.content.ContentConstants;
import com.game.community.content.mapper.TaskLogMapper;
import com.game.community.content.mapper.TaskMapper;
import com.game.community.content.service.ArticleAsyncService;
import com.game.community.content.service.TaskService;
import com.game.community.model.dto.article.ArticleDTO;
import com.game.community.model.entity.task.Task;
import com.game.community.model.entity.task.TaskLog;
import com.game.community.utils.RedisUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

/**
 * 内容任务服务：
 * 1. 任务先落数据库；
 * 2. 事务提交后再入 Redis；
 * 3. 通过定时回灌补偿 Redis 丢消息场景。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskServiceImpl implements TaskService {

    private final TaskMapper taskMapper;

    private final TaskLogMapper taskLogMapper;

    private final RedisUtils redisUtils;

    private final ArticleAsyncService articleAsyncService;

    private final ThreadPoolTaskExecutor taskExecutor;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public <T> Long addImmediateTask(int type, T param, Long businessId) {
        Task task = buildTask(type, param, businessId, null);
        taskMapper.insert(task);
        runAfterCommit(() -> enqueueImmediate(task.getId()));
        return task.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public <T> Long addDelayTask(int type, T param, Long businessId, LocalDateTime executeTime) {
        Task task = buildTask(type, param, businessId, executeTime);
        taskMapper.insert(task);
        runAfterCommit(() -> {
            if (executeTime != null && executeTime.isBefore(LocalDateTime.now().plusMinutes(5))) {
                addToDelayZSet(task.getId(), executeTime);
            }
        });
        return task.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelTask(Long taskId) {
        Task task = taskMapper.selectById(taskId);
        if (task == null || task.getStatus() != ContentConstants.TaskStatus.PENDING) {
            return;
        }
        task.setStatus(ContentConstants.TaskStatus.CANCELLED);
        task.setUpdateTime(LocalDateTime.now());
        taskMapper.updateById(task);
        redisUtils.listRemove(ContentConstants.TASK_QUEUE_KEY, taskId.toString());
        redisUtils.zRemove(ContentConstants.TASK_ZSET_KEY, taskId.toString());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void executeTask(Task task) {
        doExecuteTask(task);
    }

    @Override
    public List<Task> getPendingTasks() {
        List<Task> tasks = new ArrayList<>();
        for (int i = 0; i < ContentConstants.TASK_POP_BATCH_SIZE; i++) {
            String taskId = redisUtils.listLeftPop(ContentConstants.TASK_QUEUE_KEY);
            if (taskId == null) {
                break;
            }
            try {
                Task task = taskMapper.selectById(Long.parseLong(taskId));
                if (task != null && task.getStatus() == ContentConstants.TaskStatus.PENDING) {
                    tasks.add(task);
                }
            } catch (Exception e) {
                log.warn("读取任务失败: taskId={}, error={}", taskId, e.getMessage());
            }
        }
        return tasks;
    }

    @Override
    public void executeTasks(List<Task> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return;
        }
        List<Future<?>> futures = tasks.stream()
                .map(task -> taskExecutor.submit(() -> doExecuteTask(task)))
                .collect(Collectors.toList());
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (Exception e) {
                log.error("批量任务执行异常", e);
            }
        }
    }

    @Override
    public void recoverPendingTasks() {
        updateDatabaseToRedis();
    }

    public void updateZSetToQueue() {
        long maxScore = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
        Set<String> taskIds = redisUtils.zRangeByScore(ContentConstants.TASK_ZSET_KEY, 0, maxScore);
        for (String taskId : taskIds) {
            redisUtils.zRemove(ContentConstants.TASK_ZSET_KEY, taskId);
            redisUtils.listPushRight(ContentConstants.TASK_QUEUE_KEY, taskId);
        }
    }

    public void updateDatabaseToRedis() {
        LocalDateTime threshold = LocalDateTime.now().plusMinutes(5);
        List<Task> pendingTasks = taskMapper.selectList(new LambdaQueryWrapper<Task>()
                .eq(Task::getStatus, ContentConstants.TaskStatus.PENDING)
                .eq(Task::getQueued, 0)
                .and(wrapper -> wrapper.isNull(Task::getExecuteTime).or().le(Task::getExecuteTime, threshold))
                .orderByAsc(Task::getCreateTime)
                .last("LIMIT 500"));

        for (Task task : pendingTasks) {
            if (task.getExecuteTime() == null || !task.getExecuteTime().isAfter(LocalDateTime.now())) {
                enqueueImmediate(task.getId());
            } else {
                addToDelayZSet(task.getId(), task.getExecuteTime());
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    protected void doExecuteTask(Task task) {
        long startTime = System.currentTimeMillis();
        try {
            boolean acquired = taskMapper.update(null, new LambdaUpdateWrapper<Task>()
                    .eq(Task::getId, task.getId())
                    .eq(Task::getStatus, ContentConstants.TaskStatus.PENDING)
                    .set(Task::getStatus, ContentConstants.TaskStatus.RUNNING)
                    .set(Task::getUpdateTime, LocalDateTime.now())) == 1;
            if (!acquired) {
                return;
            }

            executeTaskByType(task);
            taskMapper.update(null, new LambdaUpdateWrapper<Task>()
                    .eq(Task::getId, task.getId())
                    .set(Task::getStatus, ContentConstants.TaskStatus.COMPLETED)
                    .set(Task::getUpdateTime, LocalDateTime.now()));
            saveTaskLog(task, 0, "success", System.currentTimeMillis() - startTime, null);
        } catch (Exception e) {
            log.error("任务执行失败: taskId={}", task.getId(), e);
            handleTaskFailure(task, e, System.currentTimeMillis() - startTime);
        }
    }

    private void executeTaskByType(Task task) {
        Map<String, Object> param = JSON.parseObject(task.getParam(), new TypeReference<Map<String, Object>>() {
        });
        if (task.getType() != ContentConstants.TaskType.ARTICLE_PUBLISH) {
            throw new IllegalArgumentException("不支持的任务类型: " + task.getType());
        }

        ArticleDTO dto = new ArticleDTO();
        dto.setId(toLong(param.get("articleId")));
        dto.setTitle((String) param.get("title"));
        dto.setSummary((String) param.get("summary"));
        dto.setContent((String) param.get("content"));
        dto.setContentParagraphs(param.get("contentParagraphs") == null
                ? java.util.Map.of()
                : JSON.parseObject(JSON.toJSONString(param.get("contentParagraphs")), new TypeReference<java.util.LinkedHashMap<String, String>>() {
        }));
        dto.setCoverUrl((String) param.get("coverUrl"));
        dto.setCategoryId(toLong(param.get("categoryId")));
        dto.setImageUrls(param.get("imageUrls") == null
                ? List.of()
                : JSON.parseObject(JSON.toJSONString(param.get("imageUrls")), new TypeReference<List<String>>() {
        }));

        articleAsyncService.auditAndPublish(
                toLong(param.get("articleId")),
                toLong(param.get("userId")),
                dto,
                dto.getCoverUrl(),
                dto.getImageUrls()
        );
    }

    private void handleTaskFailure(Task task, Exception e, long costTime) {
        Task fresh = taskMapper.selectById(task.getId());
        int retryCount = fresh == null || fresh.getRetryCount() == null ? 1 : fresh.getRetryCount() + 1;
        boolean shouldRetry = retryCount < (fresh == null || fresh.getMaxRetryCount() == null ? 3 : fresh.getMaxRetryCount());

        LambdaUpdateWrapper<Task> updateWrapper = new LambdaUpdateWrapper<Task>()
                .eq(Task::getId, task.getId())
                .set(Task::getRetryCount, retryCount)
                .set(Task::getErrorMsg, e.getMessage())
                .set(Task::getUpdateTime, LocalDateTime.now());
        if (shouldRetry) {
            updateWrapper.set(Task::getStatus, ContentConstants.TaskStatus.PENDING)
                    .set(Task::getQueued, 0);
        } else {
            updateWrapper.set(Task::getStatus, ContentConstants.TaskStatus.FAILED);
        }
        taskMapper.update(null, updateWrapper);
        saveTaskLog(task, 1, "failed", costTime, e.getMessage());

        if (shouldRetry) {
            runAfterCommit(() -> enqueueImmediate(task.getId()));
        }
    }

    private Task buildTask(int type, Object param, Long businessId, LocalDateTime executeTime) {
        Task task = new Task();
        task.setType(type);
        task.setParam(JSON.toJSONString(param));
        task.setBusinessId(businessId);
        task.setExecuteTime(executeTime);
        task.setStatus(ContentConstants.TaskStatus.PENDING);
        task.setQueued(0);
        task.setRetryCount(0);
        task.setMaxRetryCount(3);
        task.setCreateTime(LocalDateTime.now());
        task.setUpdateTime(LocalDateTime.now());
        task.setDeleted(0);
        return task;
    }

    private void enqueueImmediate(Long taskId) {
        redisUtils.listPushRight(ContentConstants.TASK_QUEUE_KEY, taskId.toString());
        taskMapper.update(null, new LambdaUpdateWrapper<Task>()
                .eq(Task::getId, taskId)
                .eq(Task::getStatus, ContentConstants.TaskStatus.PENDING)
                .set(Task::getQueued, 1)
                .set(Task::getUpdateTime, LocalDateTime.now()));
    }

    private void addToDelayZSet(Long taskId, LocalDateTime executeTime) {
        redisUtils.zAdd(ContentConstants.TASK_ZSET_KEY, taskId.toString(), executeTime.toEpochSecond(ZoneOffset.UTC));
        taskMapper.update(null, new LambdaUpdateWrapper<Task>()
                .eq(Task::getId, taskId)
                .eq(Task::getStatus, ContentConstants.TaskStatus.PENDING)
                .set(Task::getQueued, 1)
                .set(Task::getUpdateTime, LocalDateTime.now()));
    }

    private void saveTaskLog(Task task, int status, String resultMsg, long costTime, String exceptionMsg) {
        TaskLog taskLog = new TaskLog();
        taskLog.setTaskId(task.getId());
        taskLog.setType(task.getType());
        taskLog.setBusinessId(task.getBusinessId());
        taskLog.setStatus(status);
        taskLog.setResultMsg(resultMsg);
        taskLog.setCostTime(costTime);
        taskLog.setExceptionMsg(exceptionMsg);
        taskLog.setCreateTime(LocalDateTime.now());
        taskLogMapper.insert(taskLog);
    }

    private void runAfterCommit(Runnable runnable) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    runnable.run();
                }
            });
            return;
        }
        runnable.run();
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }
}
