package com.game.community.steam.schedule;

import com.game.community.steam.service.GameChartService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 国区 Steam 游戏榜单：每天 05:00 同步。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GameChartSyncScheduler {

    private final GameChartService gameChartService;

    @Scheduled(cron = "0 0 5 * * ?", zone = "Asia/Shanghai")
    public void weeklySync() {
        log.info("开始每周游戏榜单同步");
        gameChartService.syncAllCharts();
    }
}
