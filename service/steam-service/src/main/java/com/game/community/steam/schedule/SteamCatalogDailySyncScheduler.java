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

    private static final String DAILY_LOCK = "steam:catalog:daily-sync";
    private final RedisUtils redisUtils;
    private final SteamCatalogMetricsRefreshService metricsRefreshService;

    @XxlJob("steamCatalogMetricsPriceDailySyncJob")
    public void dailySync() {
        if (Boolean.FALSE.equals(redisUtils.setIfAbsent(DAILY_LOCK, "1", 3600L))) {
            return;
        }
        try {
            metricsRefreshService.refreshStaleBatch();
        } finally {
            redisUtils.del(DAILY_LOCK);
        }
    }
}
