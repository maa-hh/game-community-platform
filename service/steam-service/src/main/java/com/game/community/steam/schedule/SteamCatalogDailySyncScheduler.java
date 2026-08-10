package com.game.community.steam.schedule;

import com.game.community.common.constant.steam.SteamRedisConstants;
import com.game.community.steam.service.impl.SteamCatalogMetricsRefreshService;
import com.game.community.utils.RedisUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.springframework.stereotype.Component;

/** 每日只刷新指标和价格，不触发完整富详情抓取。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SteamCatalogDailySyncScheduler {

    private final RedisUtils redisUtils;
    private final SteamCatalogMetricsRefreshService metricsRefreshService;

    /** 由 XXL-JOB 触发每日卡片指标和价格刷新。 */
    @XxlJob("steamCatalogMetricsPriceDailySyncJob")
    public void dailySync() {
        if (Boolean.FALSE.equals(redisUtils.setIfAbsent(
                SteamRedisConstants.CATALOG_DAILY_SYNC_LOCK_KEY,
                "1",
                SteamRedisConstants.CATALOG_DAILY_SYNC_LOCK_SECONDS))) {
            return;
        }
        try {
            metricsRefreshService.refreshStaleBatch();
        } finally {
            redisUtils.del(SteamRedisConstants.CATALOG_DAILY_SYNC_LOCK_KEY);
        }
    }
}
