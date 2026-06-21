package com.game.community.content.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 服务启动后主动回灌待执行内容任务，避免服务重启后 Redis 队列丢失。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContentTaskRecoveryRunner implements ApplicationRunner {

    private final TaskService taskService;

    @Override
    public void run(ApplicationArguments args) {
        log.info("开始恢复内容待执行任务");
        taskService.recoverPendingTasks();
    }
}
