package com.game.community.steam.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.game.community.common.constant.steam.GameCatalogConstants;
import com.game.community.common.constant.steam.SteamRedisConstants;
import com.game.community.common.exception.BusinessException;
import com.game.community.feign.ContentFeignClient;
import com.game.community.model.base.Result;
import com.game.community.steam.client.SteamStoreClient;
import com.game.community.steam.event.GameSearchIndexProducer;
import com.game.community.steam.mapper.GameCatalogMapper;
import com.game.community.steam.mapper.GameReviewMapper;
import com.game.community.model.base.PageResult;
import com.game.community.model.entity.game.GameCatalog;
import com.game.community.model.vo.game.GameDetailVO;
import com.game.community.model.vo.game.GameListItemVO;
import com.game.community.model.vo.game.GameMetacriticVO;
import com.game.community.model.vo.game.GamePriceVO;
import com.game.community.model.vo.game.GameRatingStatsVO;
import com.game.community.model.vo.game.GameTagVO;
import com.game.community.steam.service.GameCatalogService;
import com.game.community.steam.service.SteamGameDetailService;
import com.game.community.utils.RedisUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Objects;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameCatalogServiceImpl implements GameCatalogService {

    private final GameCatalogMapper gameCatalogMapper;
    private final SteamStoreClient steamStoreClient;
    private final ContentFeignClient contentFeignClient;
    private final GameReviewMapper gameReviewMapper;
    private final RedisUtils redisUtils;
    private final ObjectMapper objectMapper;
    private final GameSearchIndexProducer gameSearchIndexProducer;
    private final SteamGameDetailService steamGameDetailService;
    private final SteamGameDetailRefreshService steamGameDetailRefreshService;

    @Override
    public GameDetailVO getDetail(Long appId) {
        if (appId == null) {
            throw new BusinessException("游戏 ID 无效");
        }
        String cacheKey = SteamRedisConstants.GAME_DETAIL_KEY_PREFIX + appId;
        String cached = redisUtils.get(cacheKey);
        if (StringUtils.hasText(cached)) {
            try {
                return objectMapper.readValue(cached, GameDetailVO.class);
            } catch (JsonProcessingException e) {
                log.warn("解析游戏详情缓存失败: appId={}", appId, e);
            }
        }
        GameDetailVO detail = loadDetailFromStore(appId);
        try {
            var richDetail = steamGameDetailService.find(appId);
            if (richDetail != null) {
                mergeRichDetail(detail, richDetail);
            }
            if (steamGameDetailService.needsRefresh(richDetail)) {
                steamGameDetailRefreshService.refresh(appId);
            }
        } catch (Exception e) {
            log.warn("读取 Steam Mongo 富详情失败，继续使用结构化索引: appId={}", appId, e);
        }
        cacheGameDetail(appId, detail);
        return detail;
    }

    @Async("steamDetailRefreshExecutor")
    @Override
    public void warmup(Long appId) {
        if (appId == null) {
            return;
        }
        try {
            getDetail(appId);
        } catch (Exception e) {
            log.warn("后台预热 Steam 游戏目录失败: appId={}", appId, e);
        }
    }

    private void cacheGameDetail(Long appId, GameDetailVO detail) {
        if (appId == null || detail == null) {
            return;
        }
        try {
            redisUtils.set(
                    SteamRedisConstants.GAME_DETAIL_KEY_PREFIX + appId,
                    objectMapper.writeValueAsString(detail),
                    SteamRedisConstants.GAME_DETAIL_TTL_SECONDS,
                    TimeUnit.SECONDS);
        } catch (JsonProcessingException e) {
            log.warn("写入游戏详情缓存失败: appId={}", appId, e);
        }
    }

    private void evictGameDetailCache(Long appId) {
        if (appId != null) {
            redisUtils.del(SteamRedisConstants.GAME_DETAIL_KEY_PREFIX + appId);
        }
    }

    private GameDetailVO loadDetailFromStore(Long appId) {
        GameCatalog catalog = gameCatalogMapper.selectById(appId);
        if (catalog != null && needsReviewSync(catalog) && !isStale(catalog)) {
            syncReviewSummary(catalog);
            catalog.setUpdateTime(LocalDateTime.now());
            gameCatalogMapper.updateById(catalog);
            gameSearchIndexProducer.upsertCatalog(catalog);
            return toDetailVO(catalog);
        }
        if (catalog == null || isStale(catalog)) {
            GameCatalog fetched = steamStoreClient.fetchAppDetails(appId);
            if (catalog == null) {
                fetched.setCreateTime(LocalDateTime.now());
                fetched.setUpdateTime(LocalDateTime.now());
                gameCatalogMapper.insert(fetched);
                catalog = fetched;
            } else {
                mergeSteamData(catalog, fetched);
                catalog.setSteamSyncedAt(LocalDateTime.now());
                catalog.setUpdateTime(LocalDateTime.now());
                gameCatalogMapper.updateById(catalog);
            }
            try {
                steamGameDetailService.saveFromCatalog(catalog);
            } catch (Exception e) {
                log.warn("保存 Steam Mongo 富详情失败: appId={}", appId, e);
            }
            gameSearchIndexProducer.upsert(toListItemVO(catalog));
        }
        return toDetailVO(catalog);
    }

    @Override
    public PageResult<GameListItemVO> pageGames(String sort, Long page, Long size) {
        long pageNo = page == null || page < 1 ? 1 : page;
        long pageSize = size == null || size < 1 ? 20 : Math.min(size, 50);
        LambdaQueryWrapper<GameCatalog> wrapper = new LambdaQueryWrapper<GameCatalog>()
                .eq(GameCatalog::getStatus, 1);
        applySort(wrapper, sort);
        Page<GameCatalog> result = gameCatalogMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        List<GameListItemVO> records = result.getRecords().stream().map(this::toListItemVO).toList();
        return PageResult.of(records, result.getCurrent(), result.getSize(), result.getTotal());
    }

    @Override
    public PageResult<GameListItemVO> searchGames(String keyword, Long page, Long size) {
        String q = keyword == null ? "" : keyword.trim();
        if (!StringUtils.hasText(q)) {
            return PageResult.of(List.of(), 1L, size == null ? 20L : size, 0L);
        }
        long pageNo = page == null || page < 1 ? 1 : page;
        long pageSize = size == null || size < 1 ? 20 : Math.min(size, 50);
        LambdaQueryWrapper<GameCatalog> wrapper = new LambdaQueryWrapper<GameCatalog>()
                .eq(GameCatalog::getStatus, 1)
                .and(w -> w.like(GameCatalog::getDisplayName, q)
                        .or()
                        .like(GameCatalog::getSteamName, q))
                .orderByDesc(GameCatalog::getReviewCount)
                .orderByDesc(GameCatalog::getDiscussCount);
        Page<GameCatalog> result = gameCatalogMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        List<GameListItemVO> records = result.getRecords().stream().map(this::toListItemVO).toList();
        return PageResult.of(records, result.getCurrent(), result.getSize(), result.getTotal());
    }

    @Override
    public List<GameListItemVO> searchSteamApps(String keyword, int start, int size) {
        if (!StringUtils.hasText(keyword)) {
            return List.of();
        }
        return steamStoreClient.searchApps(keyword.trim(), start, size);
    }

    @Override
    public PageResult<GameListItemVO> pageGameIndex(Long page, Long size) {
        return pageGames("hot", page, size);
    }

    private void applySort(LambdaQueryWrapper<GameCatalog> wrapper, String sort) {
        if ("score".equalsIgnoreCase(sort)) {
            wrapper.orderByDesc(GameCatalog::getAvgScore)
                    .orderByDesc(GameCatalog::getReviewCount)
                    .orderByDesc(GameCatalog::getAppId);
            return;
        }
        if ("discuss".equalsIgnoreCase(sort)) {
            wrapper.orderByDesc(GameCatalog::getDiscussCount)
                    .orderByDesc(GameCatalog::getReviewCount)
                    .orderByDesc(GameCatalog::getAppId);
            return;
        }
        wrapper.orderByDesc(GameCatalog::getUpdateTime)
                .orderByDesc(GameCatalog::getAppId);
    }

    @Override
    public GameListItemVO toListItem(GameCatalog catalog) {
        return toListItemVO(catalog);
    }

    private GameListItemVO toListItemVO(GameCatalog catalog) {
        GameListItemVO vo = new GameListItemVO();
        vo.setAppId(catalog.getAppId());
        vo.setName(resolveName(catalog));
        vo.setCoverUrl(StringUtils.hasText(catalog.getCoverOverride())
                ? catalog.getCoverOverride() : catalog.getHeaderImage());
        vo.setAvgScore(catalog.getAvgScore());
        vo.setReviewCount(catalog.getReviewCount() == null ? 0 : catalog.getReviewCount());
        vo.setDiscussCount(resolveDiscussCount(catalog));
        vo.setGenres(catalog.getGenres());
        vo.setSteamReviewScore(catalog.getSteamReviewScore());
        vo.setSteamReviewCount(catalog.getSteamReviewCount());
        vo.setDeveloper(firstOf(catalog.getDevelopers()));
        vo.setPublisher(firstOf(catalog.getPublishers()));
        vo.setReleaseDate(catalog.getReleaseDate());
        vo.setPrice(buildPriceVO(catalog));
        return vo;
    }

    private String firstOf(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.get(0);
    }

    @Override
    public void syncDiscussCount(Long appId) {
        syncDiscussCounts(List.of(appId));
    }

    @Override
    public void syncDiscussCounts(List<Long> appIds) {
        if (appIds == null || appIds.isEmpty()) {
            return;
        }
        Map<Long, Integer> countMap = fetchDiscussCountMap(appIds);
        LocalDateTime now = LocalDateTime.now();
        for (Long appId : appIds) {
            if (appId == null) {
                continue;
            }
            GameCatalog catalog = gameCatalogMapper.selectById(appId);
            if (catalog == null) {
                continue;
            }
            Integer count = countMap.get(appId);
            catalog.setDiscussCount(count == null ? 0 : count);
            catalog.setUpdateTime(now);
            gameCatalogMapper.updateById(catalog);
            evictGameDetailCache(appId);
            gameSearchIndexProducer.upsert(toListItemVO(catalog));
        }
    }

    @Override
    public List<GameTagVO> listTagsByAppIds(List<Long> appIds) {
        if (appIds == null || appIds.isEmpty()) {
            return List.of();
        }
        List<Long> distinctIds = appIds.stream().filter(Objects::nonNull).distinct().toList();
        if (distinctIds.isEmpty()) {
            return List.of();
        }
        Map<Long, GameCatalog> catalogMap = gameCatalogMapper.selectBatchIds(distinctIds).stream()
                .collect(Collectors.toMap(GameCatalog::getAppId, Function.identity(), (a, b) -> a));
        List<GameTagVO> tags = new ArrayList<>();
        for (Long appId : distinctIds) {
            GameTagVO tag = new GameTagVO();
            tag.setAppId(appId);
            GameCatalog catalog = catalogMap.get(appId);
            if (catalog != null) {
                tag.setName(StringUtils.hasText(catalog.getDisplayName())
                        ? catalog.getDisplayName() : catalog.getSteamName());
                tag.setHeaderImage(StringUtils.hasText(catalog.getCoverOverride())
                        ? catalog.getCoverOverride() : catalog.getHeaderImage());
            }
            tags.add(tag);
        }
        return tags;
    }

    private Map<Long, Integer> fetchDiscussCountMap(List<Long> appIds) {
        try {
            Result<Map<Long, Integer>> result = contentFeignClient.countPublishedDiscussByAppIds(appIds);
            if (result != null && result.getData() != null) {
                return result.getData();
            }
        } catch (Exception e) {
            // content-service 未就绪时跳过
        }
        return Map.of();
    }

    private int resolveDiscussCount(GameCatalog catalog) {
        if (catalog == null || catalog.getDiscussCount() == null) {
            return 0;
        }
        return catalog.getDiscussCount();
    }

    private String resolveName(GameCatalog catalog) {
        return StringUtils.hasText(catalog.getDisplayName())
                ? catalog.getDisplayName() : catalog.getSteamName();
    }

    private boolean needsReviewSync(GameCatalog catalog) {
        return catalog.getSteamReviewCount() == null;
    }

    private void syncReviewSummary(GameCatalog catalog) {
        SteamStoreClient.SteamReviewSummary summary =
                steamStoreClient.fetchReviewSummary(catalog.getAppId());
        if (summary == null) {
            return;
        }
        if (summary.positivePercent() != null) {
            catalog.setSteamReviewScore(summary.positivePercent());
        }
        if (summary.totalReviews() != null) {
            catalog.setSteamReviewCount(summary.totalReviews());
        }
        catalog.setMetricsSyncedAt(LocalDateTime.now());
        catalog.setLastRefreshAttemptAt(LocalDateTime.now());
        catalog.setRefreshStatus("READY");
    }

    private boolean isStale(GameCatalog catalog) {
        if (catalog.getSteamSyncedAt() == null) {
            return true;
        }
        return catalog.getSteamSyncedAt().isBefore(LocalDateTime.now().minusDays(GameCatalogConstants.STALE_DAYS));
    }

    private void mergeRichDetail(GameDetailVO target, com.game.community.model.mongo.SteamGameDetail rich) {
        if (target == null || rich == null) {
            return;
        }
        if (StringUtils.hasText(rich.getSteamShortDesc())) {
            target.setShortDescription(rich.getSteamShortDesc());
        }
        if (StringUtils.hasText(rich.getSteamAboutHtml())) {
            target.setAboutHtml(rich.getSteamAboutHtml());
        }
        target.setScreenshots(rich.getScreenshots());
        target.setMovies(rich.getMovies());
        target.setCategories(rich.getCategories());
        if (rich.getMetacriticScore() != null || StringUtils.hasText(rich.getMetacriticUrl())) {
            GameMetacriticVO metacritic = new GameMetacriticVO();
            metacritic.setScore(rich.getMetacriticScore());
            metacritic.setUrl(rich.getMetacriticUrl());
            target.setMetacritic(metacritic);
        }
        target.setAchievementTotal(rich.getAchievementTotal());
        target.setAchievementHighlights(rich.getAchievementHighlights());
        target.setPcRequirementsMin(rich.getPcRequirementsMin());
        target.setPcRequirementsRec(rich.getPcRequirementsRec());
    }

    private void mergeSteamData(GameCatalog existing, GameCatalog fetched) {
        existing.setSteamName(fetched.getSteamName());
        if (!StringUtils.hasText(existing.getDisplayName())) {
            existing.setDisplayName(fetched.getDisplayName());
        }
        existing.setSteamShortDesc(fetched.getSteamShortDesc());
        existing.setSteamAboutHtml(fetched.getSteamAboutHtml());
        existing.setHeaderImage(fetched.getHeaderImage());
        existing.setDevelopers(fetched.getDevelopers());
        existing.setPublishers(fetched.getPublishers());
        existing.setGenres(fetched.getGenres());
        existing.setReleaseDate(fetched.getReleaseDate());
        existing.setSteamUrl(fetched.getSteamUrl());
        if (!StringUtils.hasText(existing.getDescSource())) {
            existing.setDescSource(GameCatalogConstants.DESC_SOURCE_COMMUNITY_FIRST);
        }
        if (existing.getStatus() == null) {
            existing.setStatus(1);
        }
        if (fetched.getSteamReviewScore() != null) {
            existing.setSteamReviewScore(fetched.getSteamReviewScore());
        }
        if (fetched.getSteamReviewCount() != null) {
            existing.setSteamReviewCount(fetched.getSteamReviewCount());
        }
        existing.setSteamScreenshots(fetched.getSteamScreenshots());
        existing.setSteamMovies(fetched.getSteamMovies());
        existing.setSteamCategories(fetched.getSteamCategories());
        existing.setSteamIsFree(fetched.getSteamIsFree());
        existing.setPriceCurrency(fetched.getPriceCurrency());
        existing.setPriceInitial(fetched.getPriceInitial());
        existing.setPriceFinal(fetched.getPriceFinal());
        existing.setPriceDiscount(fetched.getPriceDiscount());
        existing.setPriceDiscountEndAt(fetched.getPriceDiscountEndAt());
        existing.setPriceFormatted(fetched.getPriceFormatted());
        existing.setMetacriticScore(fetched.getMetacriticScore());
        existing.setMetacriticUrl(fetched.getMetacriticUrl());
        existing.setAchievementTotal(fetched.getAchievementTotal());
        existing.setAchievementHighlights(fetched.getAchievementHighlights());
        existing.setPcRequirementsMin(fetched.getPcRequirementsMin());
        existing.setPcRequirementsRec(fetched.getPcRequirementsRec());
        existing.setStaticSyncedAt(fetched.getStaticSyncedAt());
        existing.setMetricsSyncedAt(fetched.getMetricsSyncedAt());
        existing.setPriceSyncedAt(fetched.getPriceSyncedAt());
        existing.setRichSyncedAt(fetched.getRichSyncedAt());
        existing.setLastRefreshAttemptAt(fetched.getLastRefreshAttemptAt());
        existing.setNextRefreshAt(fetched.getNextRefreshAt());
        existing.setRefreshStatus(fetched.getRefreshStatus());
        existing.setDetailReady(fetched.getDetailReady());
    }

    private GameDetailVO toDetailVO(GameCatalog catalog) {
        GameDetailVO vo = new GameDetailVO();
        vo.setAppId(catalog.getAppId());
        vo.setName(StringUtils.hasText(catalog.getDisplayName()) ? catalog.getDisplayName() : catalog.getSteamName());
        vo.setShortDescription(resolveShortDescription(catalog));
        vo.setAboutHtml(resolveAboutHtml(catalog));
        vo.setHeaderImage(StringUtils.hasText(catalog.getCoverOverride())
                ? catalog.getCoverOverride() : catalog.getHeaderImage());
        vo.setDevelopers(catalog.getDevelopers());
        vo.setPublishers(catalog.getPublishers());
        vo.setGenres(catalog.getGenres());
        vo.setReleaseDate(catalog.getReleaseDate());
        vo.setSteamUrl(catalog.getSteamUrl());
        vo.setDiscussCount(resolveDiscussCount(catalog));
        vo.setRating(buildRatingStats(catalog));
        vo.setSteamReviewScore(catalog.getSteamReviewScore());
        vo.setScreenshots(catalog.getSteamScreenshots());
        vo.setMovies(catalog.getSteamMovies());
        vo.setCategories(catalog.getSteamCategories());
        vo.setPrice(buildPriceVO(catalog));
        vo.setMetacritic(buildMetacriticVO(catalog));
        vo.setAchievementTotal(catalog.getAchievementTotal());
        vo.setAchievementHighlights(catalog.getAchievementHighlights());
        vo.setPcRequirementsMin(catalog.getPcRequirementsMin());
        vo.setPcRequirementsRec(catalog.getPcRequirementsRec());
        vo.setSteamSyncedAt(catalog.getSteamSyncedAt());
        return vo;
    }

    private GamePriceVO buildPriceVO(GameCatalog catalog) {
        if (!Boolean.TRUE.equals(catalog.getSteamIsFree())
                && catalog.getPriceFinal() == null
                && !StringUtils.hasText(catalog.getPriceFormatted())) {
            return null;
        }
        GamePriceVO vo = new GamePriceVO();
        vo.setFree(Boolean.TRUE.equals(catalog.getSteamIsFree()));
        vo.setCurrency(catalog.getPriceCurrency());
        vo.setInitial(catalog.getPriceInitial());
        vo.setFinalPrice(catalog.getPriceFinal());
        vo.setDiscountPercent(catalog.getPriceDiscount());
        vo.setDiscountEndAt(catalog.getPriceDiscountEndAt());
        vo.setFormatted(catalog.getPriceFormatted());
        return vo;
    }

    private GameMetacriticVO buildMetacriticVO(GameCatalog catalog) {
        if (catalog.getMetacriticScore() == null && !StringUtils.hasText(catalog.getMetacriticUrl())) {
            return null;
        }
        GameMetacriticVO vo = new GameMetacriticVO();
        vo.setScore(catalog.getMetacriticScore());
        vo.setUrl(catalog.getMetacriticUrl());
        return vo;
    }

    private GameRatingStatsVO buildRatingStats(GameCatalog catalog) {
        GameRatingStatsVO vo = new GameRatingStatsVO();
        if (catalog.getReviewCount() != null) {
            vo.setReviewCount(catalog.getReviewCount());
            vo.setAvgScore(catalog.getAvgScore());
            return vo;
        }
        Integer count = gameReviewMapper.selectReviewCount(catalog.getAppId());
        BigDecimal avg = gameReviewMapper.selectAvgScore(catalog.getAppId());
        vo.setReviewCount(count == null ? 0 : count);
        if (avg != null) {
            vo.setAvgScore(avg.setScale(1, RoundingMode.HALF_UP));
        }
        return vo;
    }

    private String resolveShortDescription(GameCatalog catalog) {
        if (GameCatalogConstants.DESC_SOURCE_COMMUNITY_FIRST.equalsIgnoreCase(catalog.getDescSource())
                && StringUtils.hasText(catalog.getCommunityShort())) {
            return catalog.getCommunityShort();
        }
        return catalog.getSteamShortDesc();
    }

    private String resolveAboutHtml(GameCatalog catalog) {
        if (GameCatalogConstants.DESC_SOURCE_COMMUNITY_FIRST.equalsIgnoreCase(catalog.getDescSource())
                && StringUtils.hasText(catalog.getCommunityAbout())) {
            return catalog.getCommunityAbout();
        }
        return catalog.getSteamAboutHtml();
    }
}
