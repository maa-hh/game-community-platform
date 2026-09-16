package com.game.community.steam.runner;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.game.community.common.constant.steam.SteamApiConstants;
import com.game.community.steam.client.SteamReviewClient;
import com.game.community.steam.mapper.GameCatalogMapper;
import com.game.community.model.entity.game.GameCatalog;
import com.game.community.model.enums.game.GameCatalogStatus;
import com.game.community.model.payload.steam.SteamReviewSummaryPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 启动后补全缺失的 Steam 评价人数/好评率（历史数据在新增 steam_review_count 字段前未写入）。
 */
@Slf4j
@Component
@Order(130)
@RequiredArgsConstructor
public class GameCatalogReviewBackfillRunner implements ApplicationRunner {

    private final GameCatalogMapper gameCatalogMapper;
    private final SteamReviewClient steamReviewClient;

    /** 启动后台线程补全历史目录缺失的 Steam 评价指标。 */
    @Override
    public void run(ApplicationArguments args) {
        Thread thread = new Thread(this::backfillMissingReviews, "game-review-backfill");
        thread.setDaemon(true);
        thread.start();
    }

    /** 分批查询缺失评价数据的目录，并按节奏调用 Steam 接口回填。 */
    private void backfillMissingReviews() {
        while (true) {
            List<GameCatalog> batch = gameCatalogMapper.selectList(
                    new LambdaQueryWrapper<GameCatalog>()
                            .eq(GameCatalog::getStatus, GameCatalogStatus.ENABLED.getCode())
                            .isNull(GameCatalog::getSteamReviewCount)
                            .orderByAsc(GameCatalog::getAppId)
                            .last("LIMIT " + SteamApiConstants.REVIEW_BACKFILL_BATCH_SIZE));
            if (batch.isEmpty()) {
                log.info("Steam 评价数据补全完成");
                return;
            }
            log.info("补全 Steam 评价数据: batch={}", batch.size());
            for (GameCatalog catalog : batch) {
                try {
                    SteamReviewSummaryPayload summary =
                            steamReviewClient.fetchReviewSummary(catalog.getAppId());
                    if (summary != null) {
                    if (summary.getPositivePercent() != null) {
                        catalog.setSteamReviewScore(summary.getPositivePercent());
                        }
                    if (summary.getTotalReviews() != null) {
                        catalog.setSteamReviewCount(summary.getTotalReviews());
                        }
                    } else {
                        catalog.setSteamReviewCount(0);
                    }
                    catalog.setUpdateTime(LocalDateTime.now());
                    gameCatalogMapper.updateById(catalog);
                    Thread.sleep(300);
                } catch (Exception e) {
                    log.warn("补全 Steam 评价失败 appId={}", catalog.getAppId(), e);
                }
            }
        }
    }
}
