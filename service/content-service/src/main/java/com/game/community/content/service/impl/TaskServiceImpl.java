package com.game.community.content.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.game.community.common.constant.content.ContentConstants;
import com.game.community.content.mapper.ArticleMapper;
import com.game.community.content.mapper.TaskLogMapper;
import com.game.community.content.mapper.TaskMapper;
import com.game.community.content.service.ArticleAsyncService;
import com.game.community.content.service.TaskService;
import com.game.community.model.dto.article.ArticleDTO;
import com.game.community.model.entity.article.Article;
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
import org.springframework.util.StringUtils;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.UUID;
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

    private final ArticleMapper articleMapper;

    private final TaskLogMapper taskLogMapper;

    private final RedisUtils redisUtils;

    private final ArticleAsyncService articleAsyncService;

    private final ThreadPoolTaskExecutor taskExecutor;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public <T> Long addImmediateTask(int type, T param, Long businessId) {
        Task active = findActiveTask(businessId, type);
        if (active != null) {
            return active.getId();
        }
        Task task = buildTask(type, param, businessId, null);
        try {
            taskMapper.insert(task);
        } catch (DuplicateKeyException duplicateKeyException) {
            Task concurrent = findActiveTask(businessId, type);
            if (concurrent != null) {
                return concurrent.getId();
            }
            throw duplicateKeyException;
        }
        runAfterCommit(() -> enqueueImmediate(task.getId()));
        return task.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public <T> Long addDelayTask(int type, T param, Long businessId, LocalDateTime executeTime) {
        Task active = findActiveTask(businessId, type);
        if (active != null) {
            return active.getId();
        }
        Task task = buildTask(type, param, businessId, executeTime);
        try {
            taskMapper.insert(task);
        } catch (DuplicateKeyException duplicateKeyException) {
            Task concurrent = findActiveTask(businessId, type);
            if (concurrent != null) {
                return concurrent.getId();
            }
            throw duplicateKeyException;
        }
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
        if (task == null) {
            return;
        }
        int status = task.getStatus() == null ? -1 : task.getStatus();
        if (status != ContentConstants.TaskStatus.PENDING
                && status != ContentConstants.TaskStatus.RUNNING) {
            return;
        }
        taskMapper.update(null, new LambdaUpdateWrapper<Task>()
                .eq(Task::getId, taskId)
                .in(Task::getStatus, ContentConstants.TaskStatus.PENDING, ContentConstants.TaskStatus.RUNNING)
                .set(Task::getStatus, ContentConstants.TaskStatus.CANCELLED)
                .set(Task::getLeaseToken, null)
                .set(Task::getLeaseExpireTime, null)
                .set(Task::getQueued, 0)
                .set(Task::getUpdateTime, LocalDateTime.now()));
        redisUtils.listRemove(ContentConstants.TASK_QUEUE_KEY, taskId.toString());
        redisUtils.zRemove(ContentConstants.TASK_ZSET_KEY, taskId.toString());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelTasksByBusinessId(Long businessId, int type) {
        if (businessId == null) {
            return;
        }
        taskMapper.update(null, new LambdaUpdateWrapper<Task>()
                .eq(Task::getBusinessId, businessId)
                .eq(Task::getType, type)
                .in(Task::getStatus,
                        ContentConstants.TaskStatus.PENDING,
                        ContentConstants.TaskStatus.RUNNING)
                .set(Task::getStatus, ContentConstants.TaskStatus.CANCELLED)
                .set(Task::getLeaseToken, null)
                .set(Task::getLeaseExpireTime, null)
                .set(Task::getQueued, 0)
                .set(Task::getUpdateTime, LocalDateTime.now()));
    }

    @Override
    public Integer getLatestTaskStatus(Long businessId, int type) {
        Task task = getLatestTask(businessId, type);
        return task == null ? null : task.getStatus();
    }

    @Override
    public Task getLatestTask(Long businessId, int type) {
        if (businessId == null) {
            return null;
        }
        return taskMapper.selectOne(new LambdaQueryWrapper<Task>()
                .eq(Task::getBusinessId, businessId)
                .eq(Task::getType, type)
                .orderByDesc(Task::getId)
                .last("LIMIT 1"));
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
                } else if (task != null) {
                    log.warn("跳过非待执行任务: taskId={}, status={}", taskId, task.getStatus());
                } else {
                    log.warn("队列中的任务不存在: taskId={}", taskId);
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
        recoverStaleRunningTasks();
        requeueDuePendingTasks(false);
    }

    public void updateZSetToQueue() {
        String lockToken = UUID.randomUUID().toString();
        if (!redisUtils.setIfAbsent(ContentConstants.TASK_ZSET_LOCK_KEY, lockToken, 30)) {
            return;
        }
        try {
        long maxScore = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
        Set<String> taskIds = redisUtils.zRangeByScore(ContentConstants.TASK_ZSET_KEY, 0, maxScore);
        for (String taskId : taskIds) {
            redisUtils.zRemove(ContentConstants.TASK_ZSET_KEY, taskId);
            enqueueImmediate(Long.valueOf(taskId), true);
        }
        } finally {
            redisUtils.unlock(ContentConstants.TASK_ZSET_LOCK_KEY, lockToken);
        }
    }

    public void updateDatabaseToRedis() {
        recoverStaleRunningTasks();
        recoverOrphanedQueuedTasks();
        requeueDuePendingTasks(true);
    }

    private void recoverStaleRunningTasks() {
        LocalDateTime staleBefore = LocalDateTime.now()
                .minusMinutes(ContentConstants.TASK_RUNNING_STALE_MINUTES);
        List<Task> staleRunning = taskMapper.selectList(new LambdaQueryWrapper<Task>()
                .eq(Task::getStatus, ContentConstants.TaskStatus.RUNNING)
                .and(wrapper -> wrapper.lt(Task::getLeaseExpireTime, LocalDateTime.now())
                        .or()
                        .isNull(Task::getLeaseExpireTime).lt(Task::getUpdateTime, staleBefore))
                .last("LIMIT 100"));
        for (Task task : staleRunning) {
            int updated = taskMapper.update(null, new LambdaUpdateWrapper<Task>()
                    .eq(Task::getId, task.getId())
                    .eq(Task::getStatus, ContentConstants.TaskStatus.RUNNING)
                    .set(Task::getStatus, ContentConstants.TaskStatus.PENDING)
                    .set(Task::getQueued, 0)
                    .set(Task::getLeaseToken, null)
                    .set(Task::getLeaseExpireTime, null)
                    .set(Task::getUpdateTime, LocalDateTime.now()));
            if (updated > 0) {
                log.warn("重置超时 RUNNING 任务为 PENDING: taskId={}, businessId={}",
                        task.getId(), task.getBusinessId());
            }
        }
    }

    private void recoverOrphanedQueuedTasks() {
        LocalDateTime staleBefore = LocalDateTime.now()
                .minusSeconds(ContentConstants.TASK_ORPHAN_QUEUED_STALE_SECONDS);
        List<Task> orphaned = taskMapper.selectList(new LambdaQueryWrapper<Task>()
                .eq(Task::getStatus, ContentConstants.TaskStatus.PENDING)
                .eq(Task::getQueued, 1)
                .lt(Task::getUpdateTime, staleBefore)
                .and(wrapper -> wrapper.isNull(Task::getExecuteTime)
                        .or()
                        .le(Task::getExecuteTime, LocalDateTime.now()))
                .last("LIMIT 500"));
        for (Task task : orphaned) {
            log.warn("补偿疑似丢失队列的任务: taskId={}, businessId={}",
                    task.getId(), task.getBusinessId());
            enqueueImmediate(task.getId(), true);
        }
    }

    private void requeueDuePendingTasks(boolean onlyUnqueued) {
        LocalDateTime threshold = LocalDateTime.now().plusMinutes(5);
        LambdaQueryWrapper<Task> query = new LambdaQueryWrapper<Task>()
                .eq(Task::getStatus, ContentConstants.TaskStatus.PENDING)
                .and(wrapper -> wrapper.isNull(Task::getExecuteTime).or().le(Task::getExecuteTime, threshold))
                .orderByAsc(Task::getCreateTime)
                .last("LIMIT 500");
        if (onlyUnqueued) {
            query.eq(Task::getQueued, 0);
        }

        List<Task> pendingTasks = taskMapper.selectList(query);
        for (Task task : pendingTasks) {
            if (task.getExecuteTime() == null || !task.getExecuteTime().isAfter(LocalDateTime.now())) {
                enqueueImmediate(task.getId(), !onlyUnqueued);
            } else {
                addToDelayZSet(task.getId(), task.getExecuteTime());
            }
        }
    }

    protected void doExecuteTask(Task task) {
        long startTime = System.currentTimeMillis();
        String leaseToken = UUID.randomUUID().toString().replace("-", "");
        try {
            boolean acquired = taskMapper.update(null, new LambdaUpdateWrapper<Task>()
                    .eq(Task::getId, task.getId())
                    .eq(Task::getStatus, ContentConstants.TaskStatus.PENDING)
                    .set(Task::getStatus, ContentConstants.TaskStatus.RUNNING)
                    .set(Task::getQueued, 0)
                    .set(Task::getLeaseToken, leaseToken)
                    .set(Task::getLeaseExpireTime,
                            LocalDateTime.now().plusMinutes(ContentConstants.TASK_LEASE_MINUTES))
                    .set(Task::getUpdateTime, LocalDateTime.now())) == 1;
            if (!acquired) {
                return;
            }

            executeTaskByType(task);
            int completed = taskMapper.update(null, new LambdaUpdateWrapper<Task>()
                    .eq(Task::getId, task.getId())
                    .eq(Task::getStatus, ContentConstants.TaskStatus.RUNNING)
                    .eq(Task::getLeaseToken, leaseToken)
                    .set(Task::getStatus, ContentConstants.TaskStatus.COMPLETED)
                    .set(Task::getLeaseToken, null)
                    .set(Task::getLeaseExpireTime, null)
                    .set(Task::getUpdateTime, LocalDateTime.now()));
            if (completed == 0) {
                saveTaskLog(task, 0, "cancelled_or_lease_lost", System.currentTimeMillis() - startTime, null);
                return;
            }
            saveTaskLog(task, 0, "success", System.currentTimeMillis() - startTime, null);
        } catch (Exception e) {
            log.error("任务执行失败: taskId={}", task.getId(), e);
            handleTaskFailure(task, leaseToken, e, System.currentTimeMillis() - startTime);
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
        dto.setVideoUrl((String) param.get("videoUrl"));
        dto.setPostType(param.get("postType") == null ? null : Integer.valueOf(param.get("postType").toString()));
        dto.setRefArticleId((String) param.get("refArticleId"));
        dto.setCategoryId(toLong(param.get("categoryId")));
        dto.setCategoryIds(param.get("categoryIds") == null
                ? List.of()
                : JSON.parseObject(JSON.toJSONString(param.get("categoryIds")), new TypeReference<List<Long>>() {
        }));
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

    private void handleTaskFailure(Task task, String leaseToken, Exception e, long costTime) {
        Task fresh = taskMapper.selectById(task.getId());
        if (fresh == null || !Objects.equals(fresh.getLeaseToken(), leaseToken)) {
            return;
        }
        int retryCount = fresh == null || fresh.getRetryCount() == null ? 1 : fresh.getRetryCount() + 1;
        boolean shouldRetry = retryCount < (fresh == null || fresh.getMaxRetryCount() == null ? 3 : fresh.getMaxRetryCount());

        LambdaUpdateWrapper<Task> updateWrapper = new LambdaUpdateWrapper<Task>()
                .eq(Task::getId, task.getId())
                .eq(Task::getStatus, ContentConstants.TaskStatus.RUNNING)
                .eq(Task::getLeaseToken, leaseToken)
                .set(Task::getRetryCount, retryCount)
                .set(Task::getErrorMsg, e.getMessage())
                .set(Task::getUpdateTime, LocalDateTime.now());
        if (shouldRetry) {
            updateWrapper.set(Task::getStatus, ContentConstants.TaskStatus.PENDING)
                    .set(Task::getQueued, 0)
                    .set(Task::getLeaseToken, null)
                    .set(Task::getLeaseExpireTime, null);
        } else {
            updateWrapper.set(Task::getStatus, ContentConstants.TaskStatus.FAILED)
                    .set(Task::getLeaseToken, null)
                    .set(Task::getLeaseExpireTime, null);
        }
        int updated = taskMapper.update(null, updateWrapper);
        if (updated == 0) {
            return;
        }
        saveTaskLog(task, 1, "failed", costTime, e.getMessage());

        if (!shouldRetry) {
            markArticlePublishTaskFailed(task, e.getMessage());
        }

        if (shouldRetry) {
            runAfterCommit(() -> enqueueImmediate(task.getId(), false));
        }
    }

    private void markArticlePublishTaskFailed(Task task, String errorMessage) {
        if (task == null
                || task.getType() != ContentConstants.TaskType.ARTICLE_PUBLISH
                || task.getBusinessId() == null) {
            return;
        }
        String reason = StringUtils.hasText(errorMessage) ? errorMessage.trim() : "审核发布任务失败";
        if (reason.length() > 200) {
            reason = reason.substring(0, 200);
        }
        String auditMessage = "发布处理失败：" + reason;
        int updated = articleMapper.update(null, new LambdaUpdateWrapper<Article>()
                .eq(Article::getId, task.getBusinessId())
                .eq(Article::getStatus, ContentConstants.ArticleStatus.PENDING)
                .set(Article::getStatus, ContentConstants.ArticleStatus.REJECTED)
                .set(Article::getAuditMessage, auditMessage)
                .set(Article::getUpdateTime, LocalDateTime.now()));
        if (updated > 0) {
            log.warn("文章发布任务失败，已标记为驳回: articleId={}, reason={}",
                    task.getBusinessId(), reason);
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
        enqueueImmediate(taskId, false);
    }

    private void enqueueImmediate(Long taskId, boolean force) {
        LambdaUpdateWrapper<Task> updateWrapper = new LambdaUpdateWrapper<Task>()
                .eq(Task::getId, taskId)
                .eq(Task::getStatus, ContentConstants.TaskStatus.PENDING);
        if (!force) {
            updateWrapper.eq(Task::getQueued, 0);
        }
        int updated = taskMapper.update(null, updateWrapper
                .set(Task::getQueued, 1)
                .set(Task::getUpdateTime, LocalDateTime.now()));
        if (updated > 0) {
            redisUtils.listPushRight(ContentConstants.TASK_QUEUE_KEY, taskId.toString());
        }
    }

    private void addToDelayZSet(Long taskId, LocalDateTime executeTime) {
        int updated = taskMapper.update(null, new LambdaUpdateWrapper<Task>()
                .eq(Task::getId, taskId)
                .eq(Task::getStatus, ContentConstants.TaskStatus.PENDING)
                .eq(Task::getQueued, 0)
                .set(Task::getQueued, 1)
                .set(Task::getUpdateTime, LocalDateTime.now()));
        if (updated > 0) {
            redisUtils.zAdd(ContentConstants.TASK_ZSET_KEY, taskId.toString(), executeTime.toEpochSecond(ZoneOffset.UTC));
        }
    }

    private Task findActiveTask(Long businessId, int type) {
        if (businessId == null) {
            return null;
        }
        return taskMapper.selectOne(new LambdaQueryWrapper<Task>()
                .eq(Task::getBusinessId, businessId)
                .eq(Task::getType, type)
                .in(Task::getStatus, ContentConstants.TaskStatus.PENDING, ContentConstants.TaskStatus.RUNNING)
                .orderByDesc(Task::getId)
                .last("LIMIT 1"));
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
