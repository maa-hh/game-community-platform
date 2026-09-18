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
 * 服务启动后预热当前 Redis 榜单投影。
 *
 * <p>这里不执行日/周终榜固化。终榜固化属于 XXL-JOB 的时间边界任务，不能因为
 * 某个实例重启就提前关闭窗口或覆盖历史快照。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HotArticleWarmupRunner {

    private final HotRankService hotRankService;

    @Value("${recommend.startup.warmup-redis:true}")
    private boolean warmupRedis;

    @EventListener(ApplicationReadyEvent.class)
    @Order(1)
    public void warmupHotRank() {
        if (!warmupRedis) {
            log.info("启动 Redis 热榜预热已关闭（recommend.startup.warmup-redis=false）");
            return;
        }
        runStep("总榜 Redis 投影预热", hotRankService::rebuildTotalBoard);
        runStep("日/周实时榜 Redis 投影预热", hotRankService::rebuildLivePeriodBoards);
    }

    private void runStep(String name, Runnable action) {
        try {
            log.info("启动 Redis 热榜预热开始: {}", name);
            action.run();
            log.info("启动 Redis 热榜预热完成: {}", name);
        } catch (Exception e) {
            log.warn("启动 Redis 热榜预热失败（{}），将在访问或 XXL-JOB 调度时重试: {}", name, e.getMessage());
        }
    }
}
