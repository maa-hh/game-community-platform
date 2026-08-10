package com.game.community.steam.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.game.community.common.constant.steam.SteamRedisConstants;
import com.game.community.common.constant.steam.GameCatalogConstants;
import com.game.community.model.entity.game.GameCatalog;
import com.game.community.model.enums.game.GameCatalogRefreshStatus;
import com.game.community.model.enums.game.GameCatalogStatus;
import com.game.community.model.payload.steam.SteamPricePayload;
import com.game.community.model.payload.steam.SteamReviewSummaryPayload;
import com.game.community.steam.client.SteamReviewClient;
import com.game.community.steam.client.SteamStoreClient;
import com.game.community.steam.event.GameSearchIndexProducer;
import com.game.community.steam.mapper.GameCatalogMapper;
import com.game.community.utils.RedisUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import jakarta.annotation.Resource;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * 游戏卡片指标和价格的懒更新器。
 *
 * <p>卡片只读取本地目录；首次展示或超过阈值时，这里在独立线程池中刷新 Steam
 * 数据。旧值始终保留，只有对应请求成功才替换，并通过 Redis 锁避免同一游戏并发请求。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SteamCatalogMetricsRefreshService {

    private final GameCatalogMapper gameCatalogMapper;
    private final SteamReviewClient steamReviewClient;
    private final SteamStoreClient steamStoreClient;
    private final GameSearchIndexProducer gameSearchIndexProducer;
    private final RedisUtils redisUtils;

    @Resource(name = "steamMetricsRefreshExecutor")
    private Executor metricsRefreshExecutor;

    /** 异步刷新卡片中已过期的评分人数和价格。 */
    public void refreshIfStaleAsync(List<Long> appIds) {
        if (appIds == null || appIds.isEmpty()) {
            return;
        }
        List<Long> ids = appIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .limit(GameCatalogConstants.METRICS_REFRESH_BATCH_SIZE)
                .toList();
        metricsRefreshExecutor.execute(() -> ids.forEach(this::refreshOne));
    }

    /** 启动或定时任务使用的过期数据批量刷新入口。 */
    public void refreshStaleBatchAsync() {
        metricsRefreshExecutor.execute(this::refreshStaleBatch);
    }

    /** 在 XXL-JOB 持有全局任务锁时同步处理一批过期目录。 */
    public void refreshStaleBatch() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(GameCatalogConstants.METRICS_TTL_DAYS);
        List<GameCatalog> catalogs = gameCatalogMapper.selectList(new LambdaQueryWrapper<GameCatalog>()
                .eq(GameCatalog::getStatus, GameCatalogStatus.ENABLED.getCode())
                .and(w -> w.isNull(GameCatalog::getMetricsSyncedAt)
                        .or().lt(GameCatalog::getMetricsSyncedAt, cutoff)
                        .or().isNull(GameCatalog::getPriceSyncedAt)
                        .or().lt(GameCatalog::getPriceSyncedAt, cutoff))
                .orderByAsc(GameCatalog::getMetricsSyncedAt)
                .last("LIMIT " + GameCatalogConstants.METRICS_REFRESH_BATCH_SIZE));
        catalogs.stream().map(GameCatalog::getAppId).forEach(this::refreshOne);
    }

    /** 在单游戏 Redis 锁内刷新已过期的评价指标和价格。 */
    private void refreshOne(Long appId) {
        String lockKey = SteamRedisConstants.CATALOG_METRICS_PRICE_LOCK_PREFIX + appId;
        String token = UUID.randomUUID().toString();
        Boolean acquired = redisUtils.setIfAbsent(
                lockKey, token, SteamRedisConstants.CATALOG_METRICS_PRICE_LOCK_SECONDS);
        if (!Boolean.TRUE.equals(acquired)) {
            log.debug("游戏卡片懒更新跳过，已有任务执行中: appId={}, acquired={}", appId, acquired);
            return;
        }
        try {
            GameCatalog catalog = gameCatalogMapper.selectById(appId);
            if (catalog == null) {
                log.warn("游戏卡片懒更新跳过，本地目录不存在: appId={}", appId);
                return;
            }
            if (!Integer.valueOf(GameCatalogStatus.ENABLED.getCode()).equals(catalog.getStatus())) {
                log.warn("游戏卡片懒更新跳过，目录状态不是启用: appId={}, status={}",
                        appId, catalog.getStatus());
                return;
            }
            LocalDateTime now = LocalDateTime.now();
            boolean metricsStale = isBefore(catalog.getMetricsSyncedAt(),
                    now.minusDays(GameCatalogConstants.METRICS_TTL_DAYS));
            boolean priceStale = isBefore(catalog.getPriceSyncedAt(),
                    now.minusDays(GameCatalogConstants.PRICE_TTL_DAYS));
            log.debug("游戏卡片懒更新开始: appId={}, metricsStale={}, priceStale={}",
                    appId, metricsStale, priceStale);
            boolean changed = false;
            if (metricsStale) {
                SteamReviewSummaryPayload summary =
                        steamReviewClient.fetchReviewSummary(appId);
                if (summary != null) {
                    catalog.setSteamReviewScore(summary.getPositivePercent());
                    catalog.setSteamReviewCount(summary.getTotalReviews());
                    catalog.setMetricsSyncedAt(now);
                    changed = true;
                } else {
                    log.warn("游戏卡片 Steam 评价没有返回汇总: appId={}", appId);
                }
            }
            if (priceStale) {
                SteamPricePayload price = steamStoreClient.fetchPrice(appId);
                if (price != null) {
                    catalog.setSteamIsFree(price.getFree());
                    catalog.setPriceCurrency(price.getCurrency());
                    catalog.setPriceInitial(price.getInitial());
                    catalog.setPriceFinal(price.getFinalPrice());
                    catalog.setPriceDiscount(price.getDiscountPercent());
                    catalog.setPriceDiscountEndAt(price.getDiscountEndAt());
                    catalog.setPriceFormatted(price.getFormatted());
                    catalog.setPriceSyncedAt(now);
                    changed = true;
                } else {
                    log.warn("游戏卡片 Steam 价格没有返回数据: appId={}", appId);
                }
            }
            if (changed) {
                catalog.setLastRefreshAttemptAt(now);
                catalog.setRefreshStatus(GameCatalogRefreshStatus.READY.getCode());
                catalog.setUpdateTime(now);
                gameCatalogMapper.updateById(catalog);
                // 详情缓存也包含价格和 Steam 评价，目录成功替换后必须失效，
                // 否则详情页会继续读到旧价格/旧评价人数，直到缓存自然过期。
                redisUtils.del(SteamRedisConstants.GAME_DETAIL_KEY_PREFIX + appId);
                gameSearchIndexProducer.upsertCatalog(catalog);
                log.debug("游戏卡片指标/价格懒更新完成: appId={}", appId);
            } else {
                log.debug("游戏卡片懒更新没有可写入字段: appId={}", appId);
            }
        } catch (Exception e) {
            log.warn("游戏卡片指标/价格懒更新失败: appId={}", appId, e);
        } finally {
            redisUtils.unlock(lockKey, token);
        }
    }

    /** 判断同步时间为空或早于指定阈值。 */
    private boolean isBefore(LocalDateTime value, LocalDateTime cutoff) {
        return value == null || value.isBefore(cutoff);
    }
}
