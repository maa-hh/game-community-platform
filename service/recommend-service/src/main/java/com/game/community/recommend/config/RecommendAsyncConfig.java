package com.game.community.recommend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 榜单远程调用线程池，隔离 Feign 阻塞，且限制并发规模。
 */
@Configuration
public class RecommendAsyncConfig {

    @Bean(name = "hotRankRemoteExecutor")
    public Executor hotRankRemoteExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(24);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("hot-rank-remote-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    /** 分布式维护锁续租线程，避免长时间回填或重建超过固定 TTL。 */
    @Bean(destroyMethod = "shutdown")
    public ScheduledExecutorService recommendLockRenewalExecutor() {
        return Executors.newScheduledThreadPool(1, runnable -> {
            Thread thread = new Thread(runnable, "recommend-lock-renewal");
            thread.setDaemon(true);
            return thread;
        });
    }
}
