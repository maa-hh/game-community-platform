package com.game.community.steam.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.game.community.common.constant.steam.GameCatalogConstants;
import com.game.community.common.constant.steam.SteamApiConstants;
import com.game.community.common.constant.steam.SteamRedisConstants;
import com.game.community.common.exception.BusinessException;
import com.game.community.feign.ContentFeignClient;
import com.game.community.model.base.Result;
import com.game.community.steam.client.SteamStoreClient;
import com.game.community.steam.event.GameSearchIndexProducer;
import com.game.community.steam.mapper.GameCatalogMapper;
import com.game.community.steam.mapper.GameReviewMapper;
import com.game.community.model.base.PageResult;
import com.game.community.model.dto.game.GamePageQuery;
import com.game.community.model.dto.game.GameSearchQuery;
import com.game.community.model.dto.steam.SteamAppSearchQuery;
import com.game.community.model.entity.game.GameCatalog;
import com.game.community.model.vo.game.GameDetailVO;
import com.game.community.model.vo.game.GameAchievementVO;
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
    private final SteamCatalogMetricsRefreshService steamCatalogMetricsRefreshService;

    /** 查询游戏详情，使用 Redis 缓存并按需刷新 Steam 富详情。 */
    @Override
    public GameDetailVO getDetail(Long appId) {
        if (appId == null) {
            throw new BusinessException("游戏 ID 无效");
        }
        String cacheKey = SteamRedisConstants.GAME_DETAIL_KEY_PREFIX + appId;
        String cached = redisUtils.get(cacheKey);
        if (StringUtils.hasText(cached)) {
            try {
                GameDetailVO cachedDetail = objectMapper.readValue(cached, GameDetailVO.class);
                // 旧版本缓存只包含价格和目录字段，不能继续把它当成完整详情返回。
                // 删除后走数据库/Mongo 合并逻辑，避免用户一直看到空的介绍和统计。
                if (!isIncompleteDetail(cachedDetail)) {
                    cachedDetail.setAchievementHighlights(
                            normalizeAchievementIcons(cachedDetail.getAchievementHighlights()));
                    steamCatalogMetricsRefreshService.refreshIfStaleAsync(List.of(appId));
                    steamGameDetailRefreshService.refresh(appId);
                    return cachedDetail;
                }
                redisUtils.del(cacheKey);
            } catch (JsonProcessingException e) {
                log.warn("解析游戏详情缓存失败: appId={}", appId, e);
                redisUtils.del(cacheKey);
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
        detail.setAchievementHighlights(normalizeAchievementIcons(detail.getAchievementHighlights()));
        cacheGameDetail(appId, detail);
        return detail;
    }

    /** 后台预热游戏目录，避免榜单请求同步等待 Steam 详情。 */
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

    /** 查询数据库中缺少 Steam 基础字段的游戏，调用方据此过滤外部请求。 */
    @Override
    public List<Long> findMissingBasicInfoIds(List<Long> appIds) {
        if (appIds == null || appIds.isEmpty()) {
            return List.of();
        }
        List<Long> distinctIds = appIds.stream().filter(Objects::nonNull).distinct().toList();
        if (distinctIds.isEmpty()) {
            return List.of();
        }
        Map<Long, GameCatalog> catalogMap = gameCatalogMapper.selectBatchIds(distinctIds).stream()
                .collect(Collectors.toMap(GameCatalog::getAppId, Function.identity(), (a, b) -> a));
        return distinctIds.stream()
                .filter(appId -> !hasBasicInfo(catalogMap.get(appId)))
                .toList();
    }

    /** 补充游戏公共基础信息，命中 Redis 或数据库时不再请求 Steam。 */
    @Override
    public void warmupBasicInfo(Long appId) {
        if (appId == null) {
            return;
        }
        String lockKey = SteamRedisConstants.GAME_BASIC_INFO_LOCK_PREFIX + appId;
        if (Boolean.FALSE.equals(redisUtils.setIfAbsent(
                lockKey, "1", SteamRedisConstants.GAME_BASIC_INFO_LOCK_SECONDS))) {
            return;
        }
        try {
            GameCatalog existing = gameCatalogMapper.selectById(appId);
            if (hasBasicInfo(existing)) {
                return;
            }
            GameCatalog basic = readBasicInfoCache(appId);
            if (basic == null) {
                basic = steamStoreClient.fetchBasicAppInfo(appId);
                cacheBasicInfo(appId, basic);
            }
            GameCatalog saved = saveBasicInfo(existing, basic);
            gameSearchIndexProducer.upsertCatalog(saved);
        } catch (Exception e) {
            log.warn("补充游戏基础信息失败: appId={}", appId, e);
        } finally {
            redisUtils.del(lockKey);
        }
    }

    /** 在有界线程池中补充一批缺少中英文基础字段的游戏。 */
    @Async("steamLibrarySyncExecutor")
    @Override
    public void warmupBasicInfoAsync(List<Long> appIds) {
        if (appIds == null || appIds.isEmpty()) {
            return;
        }
        for (Long appId : findMissingBasicInfoIds(appIds)) {
            warmupBasicInfo(appId);
        }
    }

    @Override
    public void refreshMetricsPriceAsync(List<Long> appIds) {
        steamCatalogMetricsRefreshService.refreshIfStaleAsync(appIds);
    }

    @Override
    public List<GameListItemVO> listCatalogItemsByAppIds(List<Long> appIds) {
        if (appIds == null || appIds.isEmpty()) {
            return List.of();
        }
        List<Long> ids = appIds.stream().filter(Objects::nonNull).distinct().limit(100).toList();
        Map<Long, GameCatalog> catalogMap = gameCatalogMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(GameCatalog::getAppId, Function.identity(), (a, b) -> a));
        return ids.stream()
                .map(catalogMap::get)
                .filter(Objects::nonNull)
                .filter(catalog -> Integer.valueOf(1).equals(catalog.getStatus()))
                .map(this::toListItemVO)
                .toList();
    }

    /** 保存 Steam 榜单或搜索返回的轻量游戏数据，不请求完整游戏详情。 */
    @Override
    public void upsertBasicCatalog(GameListItemVO game) {
        if (game == null || game.getAppId() == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        GameCatalog catalog = gameCatalogMapper.selectById(game.getAppId());
        boolean newCatalog = catalog == null;
        if (catalog == null) {
            catalog = new GameCatalog();
            catalog.setAppId(game.getAppId());
            catalog.setCreateTime(now);
            catalog.setDetailReady(false);
            catalog.setStatus(1);
            catalog.setRefreshStatus("BASIC_READY");
            // 数据库要求两个时间字段非空，但基础卡片还没有真正拉取指标/价格。
            // 写入过期时间，让后续卡片懒更新或启动批处理立即接管。
            LocalDateTime pendingRefreshAt = now.minusDays(1).minusMinutes(1);
            catalog.setMetricsSyncedAt(pendingRefreshAt);
            catalog.setPriceSyncedAt(pendingRefreshAt);
            // 基础卡片没有富详情，设置为过期状态，进入详情时由七天懒更新流程接管。
            catalog.setRichSyncedAt(now.minusDays(7).minusMinutes(1));
            catalog.setNextRefreshAt(now.minusMinutes(1));
        }
        if (StringUtils.hasText(game.getName())) {
            catalog.setSteamName(game.getName());
            catalog.setNameZh(game.getName());
            if (!StringUtils.hasText(catalog.getDisplayName())
                    || !StringUtils.hasText(catalog.getNameZh())) {
                catalog.setDisplayName(game.getName());
            }
        }
        if (shouldReplaceHeaderImage(catalog.getHeaderImage(), game.getCoverUrl())) {
            catalog.setHeaderImage(game.getCoverUrl());
        }
        applyBasicPrice(catalog, game.getPrice());
        catalog.setSteamUrl(SteamApiConstants.STEAM_STORE_APP_URL_PREFIX + game.getAppId());
        catalog.setSteamSyncedAt(now);
        catalog.setStaticSyncedAt(now);
        catalog.setLastRefreshAttemptAt(now);
        catalog.setUpdateTime(now);
        if (catalog.getStatus() == null) {
            catalog.setStatus(1);
        }
        if (newCatalog) {
            gameCatalogMapper.insert(catalog);
        } else {
            gameCatalogMapper.updateById(catalog);
        }
        gameSearchIndexProducer.upsertCatalog(catalog);
    }

    private void applyBasicPrice(GameCatalog catalog, GamePriceVO price) {
        if (!hasUsableBasicPrice(price)) {
            return;
        }
        catalog.setSteamIsFree(price.getFree());
        catalog.setPriceCurrency(price.getCurrency());
        catalog.setPriceInitial(price.getInitial());
        catalog.setPriceFinal(price.getFinalPrice());
        catalog.setPriceDiscount(price.getDiscountPercent());
        catalog.setPriceDiscountEndAt(price.getDiscountEndAt());
        catalog.setPriceFormatted(price.getFormatted());
    }

    /** 过滤榜单轻量接口中的默认 0，避免覆盖详情接口已经同步的真实价格。 */
    private boolean hasUsableBasicPrice(GamePriceVO price) {
        if (price == null) {
            return false;
        }
        if (Boolean.TRUE.equals(price.getFree())) {
            return true;
        }
        Integer initial = price.getInitial();
        Integer finalPrice = price.getFinalPrice();
        boolean hasAmount = initial != null && initial > 0
                || finalPrice != null && finalPrice > 0;
        return hasAmount;
    }

    /** 保留详情同步出的高清头图，避免榜单小图覆盖目录封面。 */
    private boolean shouldReplaceHeaderImage(String existing, String incoming) {
        if (!StringUtils.hasText(incoming)) {
            return false;
        }
        if (!StringUtils.hasText(existing)) {
            return true;
        }
        return isLowResolutionCover(existing) || !isLowResolutionCover(incoming);
    }

    private boolean isLowResolutionCover(String url) {
        String lower = url.toLowerCase();
        return lower.contains("capsule_sm_120")
                || lower.contains("capsule_231x87")
                || lower.contains("small_capsule")
                || lower.contains("/logo")
                || lower.contains("/icon");
    }

    private boolean hasBasicInfo(GameCatalog catalog) {
        return catalog != null
                && StringUtils.hasText(catalog.getSteamName())
                && StringUtils.hasText(catalog.getHeaderImage())
                && StringUtils.hasText(catalog.getNameZh())
                && StringUtils.hasText(catalog.getNameEn());
    }

    private GameCatalog readBasicInfoCache(Long appId) {
        String cached = redisUtils.get(SteamRedisConstants.GAME_BASIC_INFO_KEY_PREFIX + appId);
        if (!StringUtils.hasText(cached)) {
            return null;
        }
        try {
            return objectMapper.readValue(cached, GameCatalog.class);
        } catch (JsonProcessingException e) {
            log.warn("解析游戏基础信息缓存失败: appId={}", appId);
            redisUtils.del(SteamRedisConstants.GAME_BASIC_INFO_KEY_PREFIX + appId);
            return null;
        }
    }

    private void cacheBasicInfo(Long appId, GameCatalog basic) {
        try {
            redisUtils.set(
                    SteamRedisConstants.GAME_BASIC_INFO_KEY_PREFIX + appId,
                    objectMapper.writeValueAsString(basic),
                    SteamRedisConstants.GAME_BASIC_INFO_TTL_SECONDS,
                    TimeUnit.SECONDS);
        } catch (JsonProcessingException e) {
            log.warn("写入游戏基础信息缓存失败: appId={}", appId);
        }
    }

    private GameCatalog saveBasicInfo(GameCatalog existing, GameCatalog basic) {
        LocalDateTime now = LocalDateTime.now();
        if (existing == null) {
            basic.setCreateTime(now);
            basic.setUpdateTime(now);
            LocalDateTime pendingRefreshAt = now.minusDays(1).minusMinutes(1);
            if (basic.getMetricsSyncedAt() == null) {
                basic.setMetricsSyncedAt(pendingRefreshAt);
            }
            if (basic.getPriceSyncedAt() == null) {
                basic.setPriceSyncedAt(pendingRefreshAt);
            }
            if (basic.getRichSyncedAt() == null) {
                basic.setRichSyncedAt(now.minusDays(7).minusMinutes(1));
            }
            if (basic.getNextRefreshAt() == null) {
                basic.setNextRefreshAt(now.minusMinutes(1));
            }
            gameCatalogMapper.insert(basic);
            return basic;
        }
        existing.setSteamName(basic.getSteamName());
        existing.setNameZh(basic.getNameZh());
        existing.setNameEn(basic.getNameEn());
        if (!StringUtils.hasText(existing.getDisplayName())) {
            existing.setDisplayName(basic.getDisplayName());
        }
        existing.setHeaderImage(basic.getHeaderImage());
        existing.setDevelopers(basic.getDevelopers());
        existing.setPublishers(basic.getPublishers());
        existing.setGenres(basic.getGenres());
        existing.setReleaseDate(basic.getReleaseDate());
        existing.setSteamUrl(basic.getSteamUrl());
        existing.setSteamIsFree(basic.getSteamIsFree());
        existing.setStatus(existing.getStatus() == null ? 1 : existing.getStatus());
        existing.setSteamSyncedAt(basic.getSteamSyncedAt());
        existing.setStaticSyncedAt(basic.getStaticSyncedAt());
        existing.setLastRefreshAttemptAt(basic.getLastRefreshAttemptAt());
        if (existing.getDetailReady() == null) {
            existing.setDetailReady(false);
        }
        existing.setUpdateTime(now);
        gameCatalogMapper.updateById(existing);
        return existing;
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
        if (catalog != null && Boolean.TRUE.equals(catalog.getDetailReady()) && !isStale(catalog)) {
            var richDetail = steamGameDetailService.find(appId);
            if (steamGameDetailService.needsRefresh(richDetail)) {
                // 旧版本曾把富详情写成空 Mongo 文档；首次再次进入详情时同步修复，
                // 避免页面先返回一个空介绍、只能等用户第二次打开才能看到内容。
                try {
                    GameCatalog fetched = steamStoreClient.fetchAppDetails(appId);
                    mergeSteamData(catalog, fetched);
                    catalog.setUpdateTime(LocalDateTime.now());
                    gameCatalogMapper.updateById(catalog);
                    steamGameDetailService.saveFromCatalog(catalog);
                    gameSearchIndexProducer.upsertCatalog(catalog);
                    return toDetailVO(catalog);
                } catch (Exception e) {
                    log.warn("修复游戏富详情失败，返回旧快照并异步重试: appId={}", appId, e);
                    steamGameDetailRefreshService.refresh(appId);
                }
            }
            if (needsReviewSync(catalog)) {
                syncReviewSummary(catalog);
                catalog.setUpdateTime(LocalDateTime.now());
                gameCatalogMapper.updateById(catalog);
                gameSearchIndexProducer.upsertCatalog(catalog);
            }
            return toDetailVO(catalog);
        }
        if (catalog != null && isStale(catalog)
                && Boolean.TRUE.equals(catalog.getDetailReady())) {
            // stale-while-revalidate：旧快照继续返回，后台成功后再替换。
            steamGameDetailRefreshService.refresh(appId);
            return toDetailVO(catalog);
        }
        if (catalog == null || !Boolean.TRUE.equals(catalog.getDetailReady())) {
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

    /** 按排序方式分页查询有效游戏目录。 */
    @Override
    public PageResult<GameListItemVO> pageGames(GamePageQuery query) {
        GamePageQuery resolved = query == null ? new GamePageQuery() : query;
        long pageNo = resolved.getPage() == null || resolved.getPage() < 1
                ? 1 : resolved.getPage();
        long pageSize = resolved.getSize() == null || resolved.getSize() < 1
                ? 20 : Math.min(resolved.getSize(), 50);
        LambdaQueryWrapper<GameCatalog> wrapper = new LambdaQueryWrapper<GameCatalog>()
                .eq(GameCatalog::getStatus, 1);
        applySort(wrapper, resolved.getSort());
        Page<GameCatalog> result = gameCatalogMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        List<GameListItemVO> records = result.getRecords().stream().map(this::toListItemVO).toList();
        return PageResult.of(records, result.getCurrent(), result.getSize(), result.getTotal());
    }

    /** 按名称分页搜索有效游戏目录。 */
    @Override
    public PageResult<GameListItemVO> searchGames(GameSearchQuery query) {
        GameSearchQuery resolved = query == null ? new GameSearchQuery() : query;
        String q = resolved.getKeyword();
        if (!StringUtils.hasText(q)) {
            return PageResult.of(List.of(), 1L,
                    resolved.getSize() == null ? 20L : resolved.getSize(), 0L);
        }
        long pageNo = resolved.getPage() == null || resolved.getPage() < 1
                ? 1 : resolved.getPage();
        long pageSize = resolved.getSize() == null || resolved.getSize() < 1
                ? 20 : Math.min(resolved.getSize(), 50);
        LambdaQueryWrapper<GameCatalog> wrapper = new LambdaQueryWrapper<GameCatalog>()
                .eq(GameCatalog::getStatus, 1)
                .and(w -> w.like(GameCatalog::getDisplayName, q)
                        .or()
                        .like(GameCatalog::getNameZh, q)
                        .or()
                        .like(GameCatalog::getNameEn, q)
                        .or()
                        .like(GameCatalog::getSteamName, q))
                .orderByDesc(GameCatalog::getReviewCount)
                .orderByDesc(GameCatalog::getDiscussCount);
        Page<GameCatalog> result = gameCatalogMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        List<GameListItemVO> records = result.getRecords().stream().map(this::toListItemVO).toList();
        return PageResult.of(records, result.getCurrent(), result.getSize(), result.getTotal());
    }

    /** 调用 Steam Store 轻量搜索，不触发完整详情抓取。 */
    @Override
    public List<GameListItemVO> searchSteamApps(SteamAppSearchQuery query) {
        if (query == null || !StringUtils.hasText(query.getKeyword())) {
            return List.of();
        }
        int start = query.getStart() == null || query.getStart() < 0
                ? 0 : query.getStart();
        int size = query.getSize() == null || query.getSize() < 1
                ? 20 : Math.min(query.getSize(), 50);
        return steamStoreClient.searchApps(query.getKeyword(), start, size);
    }

    /** 为搜索服务提供固定 hot 排序的游戏目录分页数据。 */
    @Override
    public PageResult<GameListItemVO> pageGameIndex(GamePageQuery query) {
        GamePageQuery resolved = query == null ? new GamePageQuery() : query;
        resolved.setSort("hot");
        return pageGames(resolved);
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

    /** 将游戏目录实体转换为列表展示对象。 */
    @Override
    public GameListItemVO toListItem(GameCatalog catalog) {
        return toListItemVO(catalog);
    }

    private GameListItemVO toListItemVO(GameCatalog catalog) {
        GameListItemVO vo = new GameListItemVO();
        vo.setAppId(catalog.getAppId());
        vo.setName(resolveName(catalog));
        vo.setNameZh(catalog.getNameZh());
        vo.setNameEn(catalog.getNameEn());
        vo.setAliases(java.util.stream.Stream.of(catalog.getNameZh(), catalog.getNameEn())
                .filter(StringUtils::hasText)
                .distinct()
                .toList());
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

    /** 同步单个游戏的已发布讨论数。 */
    @Override
    public void syncDiscussCount(Long appId) {
        syncDiscussCounts(List.of(appId));
    }

    /** 批量同步游戏的已发布讨论数并刷新相关缓存和索引。 */
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

    /** 批量返回游戏名称和头图标签。 */
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
        if (rich.getScreenshots() != null && !rich.getScreenshots().isEmpty()) {
            target.setScreenshots(rich.getScreenshots());
        }
        if (rich.getMovies() != null && !rich.getMovies().isEmpty()) {
            target.setMovies(rich.getMovies());
        }
        if (rich.getCategories() != null && !rich.getCategories().isEmpty()) {
            target.setCategories(rich.getCategories());
        }
        if (rich.getMetacriticScore() != null || StringUtils.hasText(rich.getMetacriticUrl())) {
            GameMetacriticVO metacritic = new GameMetacriticVO();
            metacritic.setScore(rich.getMetacriticScore());
            metacritic.setUrl(rich.getMetacriticUrl());
            target.setMetacritic(metacritic);
        }
        if (rich.getAchievementTotal() != null) {
            target.setAchievementTotal(rich.getAchievementTotal());
        }
        if (rich.getAchievementHighlights() != null && !rich.getAchievementHighlights().isEmpty()) {
            target.setAchievementHighlights(normalizeAchievementIcons(rich.getAchievementHighlights()));
        }
        if (StringUtils.hasText(rich.getPcRequirementsMin())) {
            target.setPcRequirementsMin(rich.getPcRequirementsMin());
        }
        if (StringUtils.hasText(rich.getPcRequirementsRec())) {
            target.setPcRequirementsRec(rich.getPcRequirementsRec());
        }
    }

    /** 输出当前可用的 Steam 成就 CDN，兼容 Mongo、Redis 中的旧地址。 */
    private List<GameAchievementVO> normalizeAchievementIcons(List<GameAchievementVO> achievements) {
        if (achievements == null || achievements.isEmpty()) {
            return achievements;
        }
        return achievements.stream().map(item -> {
            if (item == null || !StringUtils.hasText(item.getIconUrl())) {
                return item;
            }
            item.setIconUrl(item.getIconUrl()
                    .replace("steamcdn-a.akamaihd.net", "media.steampowered.com")
                    .replace("cdn.akamai.steamstatic.com", "media.steampowered.com"));
            return item;
        }).toList();
    }

    private boolean isIncompleteDetail(GameDetailVO detail) {
        if (detail == null) {
            return true;
        }
        boolean hasDescription = StringUtils.hasText(detail.getShortDescription())
                || StringUtils.hasText(detail.getAboutHtml());
        boolean hasSupplement = detail.getScreenshots() != null && !detail.getScreenshots().isEmpty()
                || detail.getMovies() != null && !detail.getMovies().isEmpty()
                || detail.getAchievementTotal() != null
                || detail.getAchievementHighlights() != null && !detail.getAchievementHighlights().isEmpty();
        return !hasDescription || !hasSupplement;
    }

    private void mergeSteamData(GameCatalog existing, GameCatalog fetched) {
        existing.setSteamName(fetched.getSteamName());
        if (StringUtils.hasText(fetched.getNameZh())) {
            existing.setNameZh(fetched.getNameZh());
        }
        if (StringUtils.hasText(fetched.getNameEn())) {
            existing.setNameEn(fetched.getNameEn());
        }
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
        vo.setSteamReviewCount(catalog.getSteamReviewCount());
        vo.setScreenshots(catalog.getSteamScreenshots());
        vo.setMovies(catalog.getSteamMovies());
        vo.setCategories(catalog.getSteamCategories());
        vo.setPrice(buildPriceVO(catalog));
        vo.setMetacritic(buildMetacriticVO(catalog));
        vo.setAchievementTotal(catalog.getAchievementTotal());
        vo.setAchievementHighlights(normalizeAchievementIcons(catalog.getAchievementHighlights()));
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
