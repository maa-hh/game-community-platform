package com.game.community.steam.service.impl;

import com.game.community.model.entity.game.GameCatalog;
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
            GameCatalog catalog = steamStoreClient.fetchAppDetails(appId);
            if (existing != null) {
                mergeCatalog(existing, catalog);
                existing.setUpdateTime(LocalDateTime.now());
                gameCatalogMapper.updateById(existing);
                steamGameDetailService.saveFromCatalog(existing);
                gameSearchIndexProducer.upsertCatalog(existing);
            } else {
                catalog.setCreateTime(LocalDateTime.now());
                catalog.setUpdateTime(LocalDateTime.now());
                gameCatalogMapper.insert(catalog);
                steamGameDetailService.saveFromCatalog(catalog);
                gameSearchIndexProducer.upsertCatalog(catalog);
            }
            redisUtils.del(SteamRedisConstants.GAME_DETAIL_KEY_PREFIX + appId);
        } catch (Exception e) {
            log.warn("Steam 游戏富详情懒更新失败: appId={}", appId, e);
        } finally {
            redisUtils.unlock(lockKey, lockToken);
        }
    }

    private void mergeCatalog(GameCatalog target, GameCatalog source) {
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
        if (source.getSteamShortDesc() != null) {
            target.setSteamShortDesc(source.getSteamShortDesc());
        }
        if (source.getSteamAboutHtml() != null) {
            target.setSteamAboutHtml(source.getSteamAboutHtml());
        }
        if (source.getSteamScreenshots() != null && !source.getSteamScreenshots().isEmpty()) {
            target.setSteamScreenshots(source.getSteamScreenshots());
        }
        if (source.getSteamMovies() != null && !source.getSteamMovies().isEmpty()) {
            target.setSteamMovies(source.getSteamMovies());
        }
        if (source.getSteamCategories() != null && !source.getSteamCategories().isEmpty()) {
            target.setSteamCategories(source.getSteamCategories());
        }
        if (source.getMetacriticScore() != null) {
            target.setMetacriticScore(source.getMetacriticScore());
        }
        if (source.getMetacriticUrl() != null) {
            target.setMetacriticUrl(source.getMetacriticUrl());
        }
        if (source.getAchievementTotal() != null) {
            target.setAchievementTotal(source.getAchievementTotal());
        }
        if (source.getAchievementHighlights() != null && !source.getAchievementHighlights().isEmpty()) {
            target.setAchievementHighlights(source.getAchievementHighlights());
        }
        if (source.getPcRequirementsMin() != null) {
            target.setPcRequirementsMin(source.getPcRequirementsMin());
        }
        if (source.getPcRequirementsRec() != null) {
            target.setPcRequirementsRec(source.getPcRequirementsRec());
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
        target.setSteamSyncedAt(source.getSteamSyncedAt());
        target.setStaticSyncedAt(source.getStaticSyncedAt());
        target.setMetricsSyncedAt(source.getMetricsSyncedAt());
        target.setPriceSyncedAt(source.getPriceSyncedAt());
        target.setRichSyncedAt(source.getRichSyncedAt());
        target.setLastRefreshAttemptAt(source.getLastRefreshAttemptAt());
        target.setNextRefreshAt(source.getNextRefreshAt());
        target.setRefreshStatus("READY");
        target.setDetailReady(true);
    }
}
