package com.game.community.steam.schedule;

import com.game.community.common.constant.steam.SteamRedisConstants;
import com.game.community.steam.service.GameChartService;
import com.game.community.utils.RedisUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** 定时从 Steam 分批抓取榜单并落库，用户查询不触发 Steam API。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GameChartSyncScheduler {

    private final GameChartService gameChartService;
    private final RedisUtils redisUtils;

    /** 每天按配置时间同步所有榜单，使用 Redis 锁避免多实例重复抓取。 */
    @XxlJob("steamGameChartDailySyncJob")
    public void dailySync() {
        String lockToken = UUID.randomUUID().toString();
        if (Boolean.FALSE.equals(redisUtils.setIfAbsent(
                SteamRedisConstants.CHART_SYNC_LOCK_KEY,
                lockToken,
                SteamRedisConstants.CHART_SYNC_LOCK_SECONDS))) {
            log.info("已有实例执行游戏榜单同步，本实例跳过");
            return;
        }
        try {
            log.info("开始定时游戏榜单同步");
            gameChartService.syncAllCharts();
        } finally {
            redisUtils.unlock(SteamRedisConstants.CHART_SYNC_LOCK_KEY, lockToken);
        }
    }
}
