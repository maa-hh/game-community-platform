package com.game.community.steam.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.game.community.common.constant.steam.SteamRedisConstants;
import com.game.community.model.entity.game.GameCatalog;
import com.game.community.model.vo.game.GamePriceVO;
import com.game.community.steam.client.SteamStoreClient;
import com.game.community.steam.event.GameSearchIndexProducer;
import com.game.community.steam.mapper.GameCatalogMapper;
import com.game.community.utils.RedisUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/** 每日只刷新指标和价格，不触发完整富详情抓取。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SteamCatalogDailySyncScheduler {

    private static final String DAILY_LOCK = "steam:catalog:daily-sync";
    private static final int BATCH_SIZE = 100;

    private final GameCatalogMapper gameCatalogMapper;
    private final SteamStoreClient steamStoreClient;
    private final GameSearchIndexProducer gameSearchIndexProducer;
    private final RedisUtils redisUtils;

    @Scheduled(cron = "0 0 3 * * ?", zone = "Asia/Shanghai")
    public void dailySync() {
        if (Boolean.FALSE.equals(redisUtils.setIfAbsent(DAILY_LOCK, "1", 3600L))) {
            return;
        }
        try {
            syncMetrics();
            syncPrices();
        } finally {
            redisUtils.del(DAILY_LOCK);
        }
    }

    private void syncMetrics() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(1);
        List<GameCatalog> games = gameCatalogMapper.selectList(new LambdaQueryWrapper<GameCatalog>()
                .eq(GameCatalog::getStatus, 1)
                .and(w -> w.isNull(GameCatalog::getMetricsSyncedAt)
                        .or().lt(GameCatalog::getMetricsSyncedAt, cutoff))
                .orderByAsc(GameCatalog::getMetricsSyncedAt)
                .last("LIMIT " + BATCH_SIZE));
        for (GameCatalog game : games) {
            try {
                SteamStoreClient.SteamReviewSummary summary =
                        steamStoreClient.fetchReviewSummary(game.getAppId());
                if (summary == null) {
                    game.setLastRefreshAttemptAt(LocalDateTime.now());
                    game.setRefreshStatus("FAILED");
                    gameCatalogMapper.updateById(game);
                    continue;
                }
                game.setSteamReviewScore(summary.positivePercent());
                game.setSteamReviewCount(summary.totalReviews());
                game.setMetricsSyncedAt(LocalDateTime.now());
                game.setLastRefreshAttemptAt(LocalDateTime.now());
                game.setRefreshStatus("READY");
                gameCatalogMapper.updateById(game);
                gameSearchIndexProducer.upsert(toListItem(game));
            } catch (Exception e) {
                log.warn("Steam 评分日更失败: appId={}", game.getAppId(), e);
            }
        }
    }

    private void syncPrices() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(1);
        List<GameCatalog> games = gameCatalogMapper.selectList(new LambdaQueryWrapper<GameCatalog>()
                .eq(GameCatalog::getStatus, 1)
                .and(w -> w.isNull(GameCatalog::getPriceSyncedAt)
                        .or().lt(GameCatalog::getPriceSyncedAt, cutoff))
                .orderByAsc(GameCatalog::getPriceSyncedAt)
                .last("LIMIT " + BATCH_SIZE));
        for (GameCatalog game : games) {
            try {
                GamePriceVO price = steamStoreClient.fetchPrice(game.getAppId());
                if (price != null) {
                    game.setSteamIsFree(price.getFree());
                    game.setPriceCurrency(price.getCurrency());
                    game.setPriceInitial(price.getInitial());
                    game.setPriceFinal(price.getFinalPrice());
                    game.setPriceDiscount(price.getDiscountPercent());
                    game.setPriceDiscountEndAt(price.getDiscountEndAt());
                    game.setPriceFormatted(price.getFormatted());
                }
                game.setPriceSyncedAt(LocalDateTime.now());
                game.setLastRefreshAttemptAt(LocalDateTime.now());
                game.setRefreshStatus("READY");
                gameCatalogMapper.updateById(game);
                gameSearchIndexProducer.upsert(toListItem(game));
            } catch (Exception e) {
                log.warn("Steam 价格日更失败: appId={}", game.getAppId(), e);
            }
        }
    }

    private com.game.community.model.vo.game.GameListItemVO toListItem(GameCatalog game) {
        com.game.community.model.vo.game.GameListItemVO item = new com.game.community.model.vo.game.GameListItemVO();
        item.setAppId(game.getAppId());
        item.setName(org.springframework.util.StringUtils.hasText(game.getDisplayName())
                ? game.getDisplayName() : game.getSteamName());
        item.setCoverUrl(org.springframework.util.StringUtils.hasText(game.getCoverOverride())
                ? game.getCoverOverride() : game.getHeaderImage());
        item.setGenres(game.getGenres());
        item.setDeveloper(game.getDevelopers() == null || game.getDevelopers().isEmpty()
                ? null : game.getDevelopers().get(0));
        item.setPublisher(game.getPublishers() == null || game.getPublishers().isEmpty()
                ? null : game.getPublishers().get(0));
        item.setReleaseDate(game.getReleaseDate());
        item.setSteamReviewScore(game.getSteamReviewScore());
        item.setSteamReviewCount(game.getSteamReviewCount());
        item.setAvgScore(game.getAvgScore());
        item.setReviewCount(game.getReviewCount());
        item.setDiscussCount(game.getDiscussCount());
        if (game.getPriceFinal() != null || game.getPriceInitial() != null
                || game.getSteamIsFree() != null) {
            GamePriceVO price = new GamePriceVO();
            price.setFree(game.getSteamIsFree());
            price.setCurrency(game.getPriceCurrency());
            price.setInitial(game.getPriceInitial());
            price.setFinalPrice(game.getPriceFinal());
            price.setDiscountPercent(game.getPriceDiscount());
            price.setDiscountEndAt(game.getPriceDiscountEndAt());
            price.setFormatted(game.getPriceFormatted());
            item.setPrice(price);
        }
        return item;
    }
}
