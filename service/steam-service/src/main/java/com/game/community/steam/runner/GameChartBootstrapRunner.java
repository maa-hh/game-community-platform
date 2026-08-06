package com.game.community.steam.runner;

import com.game.community.steam.service.GameChartService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 启动时若本地无榜单快照则触发首次同步。
 */
@Slf4j
@Component
@Order(120)
@RequiredArgsConstructor
public class GameChartBootstrapRunner implements ApplicationRunner {

    private final GameChartService gameChartService;

    @Override
    public void run(ApplicationArguments args) {
        if (!gameChartService.isAnyChartEmpty()) {
            return;
        }
        log.info("游戏榜单快照为空，触发首次同步");
        gameChartService.syncAllCharts();
    }
}
