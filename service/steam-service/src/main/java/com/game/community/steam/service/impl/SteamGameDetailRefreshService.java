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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SteamGameDetailRefreshService {

    private final SteamStoreClient steamStoreClient;
    private final SteamGameDetailService steamGameDetailService;
    private final RedisUtils redisUtils;
    private final GameCatalogMapper gameCatalogMapper;
    private final GameSearchIndexProducer gameSearchIndexProducer;

    /** 异步刷新指定游戏的 Steam 富详情，并在成功后更新目录与搜索索引。 */
    @Async("steamDetailRefreshExecutor")
    public void refresh(Long appId) {
        if (appId == null) {
            return;
        }
        String lockKey = SteamRedisConstants.DETAIL_REFRESH_LOCK_PREFIX + appId;
        String lockToken = UUID.randomUUID().toString();
        if (Boolean.FALSE.equals(redisUtils.setIfAbsent(
                lockKey, lockToken, SteamRedisConstants.DETAIL_REFRESH_LOCK_SECONDS))) {
            return;
        }
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
        } finally {
            redisUtils.unlock(lockKey, lockToken);
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
