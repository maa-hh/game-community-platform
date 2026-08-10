package com.game.community.steam.runner;

import com.game.community.steam.schedule.GameChartSyncScheduler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Steam 服务启动后预热榜单，并推动一批过期卡片指标进入懒更新队列。 */
@Slf4j
@Component
@Order(140)
@RequiredArgsConstructor
public class SteamCatalogStartupSyncRunner implements ApplicationRunner {

    private final GameChartSyncScheduler gameChartSyncScheduler;

    @Override
    public void run(ApplicationArguments args) {
        Thread thread = new Thread(() -> {
            try {
                log.info("Steam 服务启动预热：开始同步各游戏榜单");
                gameChartSyncScheduler.dailySync();
                log.info("Steam 服务启动预热：榜单同步任务已提交");
            } catch (Exception e) {
                log.warn("Steam 服务启动预热失败，等待 XXL-JOB 下次重试", e);
            }
        }, "steam-startup-sync");
        thread.setDaemon(true);
        thread.start();
    }
}
