package com.game.community.steam.service.impl;

import com.game.community.model.entity.game.GameCatalog;
import com.game.community.model.enums.game.GameCatalogRefreshStatus;
import com.game.community.model.enums.game.GameCatalogStatus;
import com.game.community.model.payload.steam.SteamGameDetailsPayload;
import com.game.community.common.constant.steam.GameCatalogConstants;
import com.game.community.common.constant.steam.SteamRedisConstants;
import com.game.community.steam.client.SteamStoreClient;
import com.game.community.steam.event.GameSearchIndexProducer;
import com.game.community.steam.mapper.GameCatalogMapper;
import com.game.community.steam.service.SteamGameDetailService;
import com.game.community.utils.RedisUtils;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class SteamGameDetailRefreshService {

    private final SteamStoreClient steamStoreClient;
    private final SteamGameDetailService steamGameDetailService;
    private final RedisUtils redisUtils;
    private final GameCatalogMapper gameCatalogMapper;
    private final GameSearchIndexProducer gameSearchIndexProducer;

    @Resource(name = "steamDetailRefreshExecutor")
    private Executor detailRefreshExecutor;

    /** 同一 JVM 内按 appId 合并重复详情刷新任务，避免重复任务先进入线程池再被丢弃。 */
    private final Map<Long, CompletableFuture<Void>> inFlight = new ConcurrentHashMap<>();

    /** 异步刷新指定游戏的 Steam 富详情，并在成功后更新目录与搜索索引。 */
    public CompletableFuture<Void> refresh(Long appId) {
        if (appId == null) {
            return CompletableFuture.completedFuture(null);
        }
        try {
            return inFlight.computeIfAbsent(appId, this::submitRefresh);
        } catch (RejectedExecutionException e) {
            log.warn("Steam 游戏富详情刷新线程池已满: appId={}", appId);
            return CompletableFuture.failedFuture(e);
        }
    }

    /** 将单个游戏详情刷新任务提交到有界线程池，并在结束后释放本地合并状态。 */
    private CompletableFuture<Void> submitRefresh(Long appId) {
        CompletableFuture<Void> future = CompletableFuture.runAsync(
                () -> refreshWithDistributedLock(appId), detailRefreshExecutor);
        future.whenComplete((result, error) -> inFlight.remove(appId, future));
        return future;
    }

    /** 在本地 SingleFlight 之外，再用 Redis 锁协调多实例刷新。 */
    private void refreshWithDistributedLock(Long appId) {
        GameCatalog before = gameCatalogMapper.selectById(appId);
        String lockKey = SteamRedisConstants.DETAIL_REFRESH_LOCK_PREFIX + appId;
        String lockToken = UUID.randomUUID().toString();
        if (Boolean.TRUE.equals(redisUtils.setIfAbsent(
                lockKey, lockToken, SteamRedisConstants.DETAIL_REFRESH_LOCK_SECONDS))) {
            try {
                refreshAsOwner(appId);
                return;
            } finally {
                redisUtils.unlock(lockKey, lockToken);
            }
        }

        // 已有其他实例持锁时先等待结果；持锁实例失败或锁过期后，等待者通过 SETNX 原子接管。
        // 同一时刻仍只有一个实例能成为 owner，既不重复并发请求，也不会因 owner 失败而永久无结果。
        long deadline = System.currentTimeMillis() + SteamRedisConstants.DETAIL_REFRESH_WAIT_MILLIS;
        while (System.currentTimeMillis() <= deadline) {
            if (isCompletedSince(before, appId)) {
                return;
            }
            if (redisUtils.get(lockKey) == null
                    && Boolean.TRUE.equals(redisUtils.setIfAbsent(
                    lockKey, lockToken, SteamRedisConstants.DETAIL_REFRESH_LOCK_SECONDS))) {
                try {
                    refreshAsOwner(appId);
                    return;
                } finally {
                    redisUtils.unlock(lockKey, lockToken);
                }
            }
            sleepBeforeRetry();
        }
        log.info("Steam 游戏富详情已有其他实例刷新，本次等待超时: appId={}", appId);
    }

    /** 持有分布式锁后再次检查，避免锁等待期间已经完成刷新。 */
    private void refreshAsOwner(Long appId) {
        try {
            GameCatalog existing = gameCatalogMapper.selectById(appId);
            var existingDetail = steamGameDetailService.find(appId);
            if (existing != null
                    && Boolean.TRUE.equals(existing.getDetailReady())
                    && !steamGameDetailService.needsRefresh(existingDetail)) {
                return;
            }
            SteamGameDetailsPayload payload = steamStoreClient.fetchAppDetails(appId);
            if (existing != null) {
                mergeCatalog(existing, payload);
                existing.setUpdateTime(LocalDateTime.now());
                gameCatalogMapper.updateById(existing);
                steamGameDetailService.save(payload);
                gameSearchIndexProducer.upsertCatalog(existing);
            } else {
                GameCatalog catalog = toCatalog(payload);
                catalog.setCreateTime(LocalDateTime.now());
                catalog.setUpdateTime(LocalDateTime.now());
                gameCatalogMapper.insert(catalog);
                steamGameDetailService.save(payload);
                gameSearchIndexProducer.upsertCatalog(catalog);
            }
            redisUtils.del(SteamRedisConstants.GAME_DETAIL_KEY_PREFIX + appId);
        } catch (Exception e) {
            log.warn("Steam 游戏富详情懒更新失败: appId={}", appId, e);
        }
    }

    /** 判断其他实例是否已经将详情版本推进。 */
    private boolean isCompletedSince(GameCatalog before, Long appId) {
        GameCatalog current = gameCatalogMapper.selectById(appId);
        if (current == null) {
            return false;
        }
        if (before == null) {
            return Boolean.TRUE.equals(current.getDetailReady());
        }
        if (!Boolean.TRUE.equals(before.getDetailReady())) {
            return Boolean.TRUE.equals(current.getDetailReady());
        }
        return current.getSteamSyncedAt() != null
                && (before.getSteamSyncedAt() == null
                || current.getSteamSyncedAt().isAfter(before.getSteamSyncedAt()));
    }

    /** 轮询其他实例刷新结果前短暂休眠，避免持续查询数据库。 */
    private void sleepBeforeRetry() {
        try {
            TimeUnit.MILLISECONDS.sleep(SteamRedisConstants.DETAIL_REFRESH_POLL_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待 Steam 游戏详情刷新被中断", e);
        }
    }

    /** 将 Steam 富详情中的持久化字段合并到已有游戏目录。 */
    private void mergeCatalog(GameCatalog target, SteamGameDetailsPayload source) {
        if (source.getSteamName() != null) {
            target.setSteamName(source.getSteamName());
        }
        if (source.getNameZh() != null) {
            target.setNameZh(source.getNameZh());
        }
        if (source.getNameEn() != null) {
            target.setNameEn(source.getNameEn());
        }
        if (target.getDisplayName() == null || target.getDisplayName().isBlank()) {
            target.setDisplayName(source.getDisplayName());
        }
        if (source.getHeaderImage() != null) {
            target.setHeaderImage(source.getHeaderImage());
        }
        if (source.getDevelopers() != null && !source.getDevelopers().isEmpty()) {
            target.setDevelopers(source.getDevelopers());
        }
        if (source.getPublishers() != null && !source.getPublishers().isEmpty()) {
            target.setPublishers(source.getPublishers());
        }
        if (source.getGenres() != null && !source.getGenres().isEmpty()) {
            target.setGenres(source.getGenres());
        }
        if (source.getReleaseDate() != null) {
            target.setReleaseDate(source.getReleaseDate());
        }
        if (source.getSteamUrl() != null) {
            target.setSteamUrl(source.getSteamUrl());
        }
        if (source.getSteamReviewScore() != null) {
            target.setSteamReviewScore(source.getSteamReviewScore());
        }
        if (source.getSteamReviewCount() != null) {
            target.setSteamReviewCount(source.getSteamReviewCount());
        }
        target.setSteamIsFree(source.getSteamIsFree());
        target.setPriceCurrency(source.getPriceCurrency());
        target.setPriceInitial(source.getPriceInitial());
        target.setPriceFinal(source.getPriceFinal());
        target.setPriceDiscount(source.getPriceDiscount());
        target.setPriceDiscountEndAt(source.getPriceDiscountEndAt());
        target.setPriceFormatted(source.getPriceFormatted());
        LocalDateTime now = LocalDateTime.now();
        target.setSteamSyncedAt(now);
        target.setStaticSyncedAt(now);
        target.setMetricsSyncedAt(now);
        target.setPriceSyncedAt(now);
        target.setRichSyncedAt(now);
        target.setLastRefreshAttemptAt(now);
        target.setNextRefreshAt(now.plusDays(7));
        target.setRefreshStatus(GameCatalogRefreshStatus.READY.getCode());
        target.setDetailReady(true);
    }

    /** 将 Steam 富详情 payload 转换为新的游戏目录实体。 */
    private GameCatalog toCatalog(SteamGameDetailsPayload source) {
        GameCatalog target = new GameCatalog();
        target.setAppId(source.getAppId());
        target.setSteamName(source.getSteamName());
        target.setNameZh(source.getNameZh());
        target.setNameEn(source.getNameEn());
        target.setDisplayName(source.getDisplayName());
        target.setHeaderImage(source.getHeaderImage());
        target.setDevelopers(source.getDevelopers());
        target.setPublishers(source.getPublishers());
        target.setGenres(source.getGenres());
        target.setReleaseDate(source.getReleaseDate());
        target.setSteamUrl(source.getSteamUrl());
        target.setSteamReviewScore(source.getSteamReviewScore());
        target.setSteamReviewCount(source.getSteamReviewCount());
        target.setSteamIsFree(source.getSteamIsFree());
        target.setPriceCurrency(source.getPriceCurrency());
        target.setPriceInitial(source.getPriceInitial());
        target.setPriceFinal(source.getPriceFinal());
        target.setPriceDiscount(source.getPriceDiscount());
        target.setPriceDiscountEndAt(source.getPriceDiscountEndAt());
        target.setPriceFormatted(source.getPriceFormatted());
        target.setStatus(GameCatalogStatus.ENABLED.getCode());
        target.setDescSource(GameCatalogConstants.DESC_SOURCE_COMMUNITY_FIRST);
        target.setRefreshStatus(GameCatalogRefreshStatus.READY.getCode());
        target.setDetailReady(true);
        LocalDateTime now = LocalDateTime.now();
        target.setSteamSyncedAt(now);
        target.setStaticSyncedAt(now);
        target.setMetricsSyncedAt(now);
        target.setPriceSyncedAt(now);
        target.setRichSyncedAt(now);
        target.setLastRefreshAttemptAt(now);
        target.setNextRefreshAt(now.plusDays(7));
        return target;
    }
}
