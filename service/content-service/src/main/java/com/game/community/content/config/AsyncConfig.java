package com.game.community.content.config;

import com.game.community.common.constant.content.ContentConstants;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.Executor;

/**
 * 异步任务线程池配置
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * 任务执行线程池（批量并发执行）
     */
    @Bean
    public ThreadPoolTaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(ContentConstants.TASK_THREAD_POOL_SIZE);
        executor.setMaxPoolSize(ContentConstants.TASK_THREAD_POOL_SIZE);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("content-task-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }

    /** 外部服务调用隔离线程池，避免 AI/Feign 阻塞任务 worker。 */
    @Bean(name = "contentRemoteExecutor")
    public Executor contentRemoteExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(32);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("content-remote-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }

    /** 图片直传使用独立线程池并发写 MinIO，避免占满审核/业务任务线程。 */
    @Bean(name = "contentFileUploadExecutor")
    public Executor contentFileUploadExecutor(
            @Value("${content.file-upload.concurrency:8}") int concurrency) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        int poolSize = Math.max(1, concurrency);
        executor.setCorePoolSize(poolSize);
        executor.setMaxPoolSize(poolSize);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("content-file-upload-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }

    /** 大文件分片写 MinIO 的独立线程池，避免上传流量占满审核任务和图片上传线程。 */
    @Bean(name = "contentChunkUploadExecutor")
    public ThreadPoolTaskExecutor contentChunkUploadExecutor(
            @Value("${content.file-upload.chunk-concurrency:8}") int concurrency,
            @Value("${content.file-upload.chunk-queue-capacity:64}") int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        int poolSize = Math.max(1, concurrency);
        executor.setCorePoolSize(poolSize);
        executor.setMaxPoolSize(poolSize);
        executor.setQueueCapacity(Math.max(0, queueCapacity));
        executor.setThreadNamePrefix("content-chunk-upload-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }
}
