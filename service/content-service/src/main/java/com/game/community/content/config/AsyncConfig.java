package com.game.community.content.config;

import com.game.community.common.constant.content.ContentConstants;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步任务线程池配置
 */
@Configuration
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
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
