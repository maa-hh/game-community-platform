package com.game.community.search.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
@EnableScheduling
public class SearchAsyncConfig {

    @Bean("taskExecutor")
    public Executor searchEventExecutor() {
        return executor("search-event-", 4, 16, 1000);
    }

    @Bean("aiExecutor")
    public Executor aiExecutor() {
        return executor("search-ai-", 2, 8, 200);
    }

    @Bean("searchSyncExecutor")
    public Executor searchSyncExecutor() {
        return executor("search-sync-", 1, 1, 1);
    }

    @Bean("gameIndexExecutor")
    public Executor gameIndexExecutor() {
        return executor("game-index-", 2, 4, 200);
    }

    private ThreadPoolTaskExecutor executor(String prefix, int core, int max, int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(core);
        executor.setMaxPoolSize(max);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix(prefix);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
