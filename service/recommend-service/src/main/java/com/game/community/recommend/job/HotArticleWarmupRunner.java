package com.game.community.recommend.job;

import com.game.community.recommend.service.HotRankService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 服务启动后执行一次热榜初始化任务（等同 XXL-JOB 三个新 Job，便于本地/重启后立即可用）。
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class HotArticleWarmupRunner {

    private final HotRankService hotRankService;

    @Value("${recommend.startup.run-hot-rank-jobs:true}")
    private boolean runHotRankJobs;

    @EventListener(ApplicationReadyEvent.class)
    public void warmupHotRank() {
        if (!runHotRankJobs) {
            log.info("启动热榜任务已关闭（recommend.startup.run-hot-rank-jobs=false）");
            return;
        }
        runStep("日榜终榜固化", () -> hotRankService.finalizeDailyBoard(null));
        runStep("周榜固化", hotRankService::finalizeWeeklyBoard);
        runStep("总榜重建", hotRankService::rebuildTotalBoard);
        runStep("日/周实时榜重建", hotRankService::rebuildLivePeriodBoards);
    }

    private void runStep(String name, Runnable action) {
        try {
            log.info("启动热榜任务开始: {}", name);
            action.run();
            log.info("启动热榜任务完成: {}", name);
        } catch (Exception e) {
            log.warn("启动热榜任务失败（{}），将在访问或 XXL-JOB 调度时重试: {}", name, e.getMessage());
        }
    }
}
