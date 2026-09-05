package com.game.community.steam.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.community.common.constant.ApiErrorCodes;
import com.game.community.common.constant.steam.GameCatalogConstants;
import com.game.community.common.constant.steam.SteamApiConstants;
import com.game.community.common.constant.steam.SteamRedisConstants;
import com.game.community.common.exception.BusinessException;
import com.game.community.feign.ContentFeignClient;
import com.game.community.model.base.Result;
import com.game.community.steam.client.SteamChartClient;
import com.game.community.steam.client.SteamStoreClient;
import com.game.community.steam.event.GameSearchIndexProducer;
import com.game.community.steam.mapper.GameCatalogMapper;
import com.game.community.steam.mapper.GameReviewMapper;
import com.game.community.model.base.PageResult;
import com.game.community.model.dto.game.GamePageQuery;
import com.game.community.model.dto.game.GameSearchQuery;
import com.game.community.model.dto.steam.SteamAppSearchQuery;
import com.game.community.model.entity.game.GameCatalog;
import com.game.community.model.enums.game.GameCatalogRefreshStatus;
import com.game.community.model.enums.game.GameCatalogStatus;
import com.game.community.model.mongo.SteamGameDetail;
import com.game.community.model.payload.steam.SteamChartGamePayload;
import com.game.community.model.payload.steam.SteamGameBasicPayload;
import com.game.community.model.payload.steam.SteamGameDetailsPayload;
import com.game.community.model.payload.steam.SteamPricePayload;
import com.game.community.model.vo.game.GameDetailVO;
import com.game.community.model.vo.game.GameAchievementVO;
import com.game.community.model.vo.game.GameListItemVO;
import com.game.community.model.vo.game.GameMovieVO;
import com.game.community.model.vo.game.GameMetacriticVO;
import com.game.community.model.vo.game.GamePriceVO;
import com.game.community.model.vo.game.GameRatingStatsVO;
import com.game.community.model.vo.game.GameScreenshotVO;
import com.game.community.model.vo.game.GameTagVO;
import com.game.community.steam.service.GameCatalogService;
import com.game.community.steam.service.SteamGameDetailService;
import com.game.community.steam.util.SteamAchievementIconUrl;
import com.game.community.utils.RedisUtils;
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
    private final SteamChartClient steamChartClient;
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
                if (Boolean.FALSE.equals(cachedDetail.getDetailReady())) {
                    // 待补全快照不能缓存五分钟，否则后台完成后轮询仍会反复读到旧状态。
                    redisUtils.del(cacheKey);
                    cachedDetail.setAchievementHighlights(
                            normalizeAchievementIcons(cachedDetail.getAchievementHighlights()));
                    steamCatalogMetricsRefreshService.refreshIfStaleAsync(List.of(appId));
                    steamGameDetailRefreshService.refresh(appId);
                    return cachedDetail;
                }
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
        if (!Boolean.FALSE.equals(detail.getDetailReady())) {
            cacheGameDetail(appId, detail);
        }
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
            if (StringUtils.hasText(redisUtils.get(
                    SteamRedisConstants.GAME_BASIC_INFO_UNAVAILABLE_KEY_PREFIX + appId))) {
                return;
            }
            SteamGameBasicPayload basic = readBasicInfoCache(appId);
            if (basic == null) {
                basic = steamStoreClient.fetchBasicAppInfo(appId);
                cacheBasicInfo(appId, basic);
            }
            GameCatalog saved = saveBasicInfo(existing, basic);
            gameSearchIndexProducer.upsertCatalog(saved);
        } catch (BusinessException e) {
            if (e.getCode() == ApiErrorCodes.NOT_FOUND) {
                redisUtils.set(
                        SteamRedisConstants.GAME_BASIC_INFO_UNAVAILABLE_KEY_PREFIX + appId,
                        "1",
                        SteamRedisConstants.GAME_BASIC_INFO_UNAVAILABLE_TTL_SECONDS,
                        TimeUnit.SECONDS);
                log.info("Steam 商店无该 App，跳过基础信息预热: appId={}", appId);
                return;
            }
            log.warn("补充游戏基础信息失败: appId={}", appId, e);
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

    /** 触发一批游戏的价格和 Steam 评价指标懒更新。 */
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
                .filter(catalog -> Integer.valueOf(GameCatalogStatus.ENABLED.getCode()).equals(catalog.getStatus()))
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
            catalog.setStatus(GameCatalogStatus.ENABLED.getCode());
            catalog.setRefreshStatus(GameCatalogRefreshStatus.BASIC_READY.getCode());
            // 数据库要求两个时间字段非空，但基础卡片还没有真正拉取指标/价格。
            // 写入过期时间，让后续卡片懒更新或启动批处理立即接管。
            LocalDateTime pendingRefreshAt = now.minusDays(GameCatalogConstants.METRICS_TTL_DAYS)
                    .minusMinutes(GameCatalogConstants.REFRESH_GRACE_MINUTES);
            catalog.setMetricsSyncedAt(pendingRefreshAt);
            catalog.setPriceSyncedAt(pendingRefreshAt);
            // 基础卡片没有富详情，设置为过期状态，进入详情时由七天懒更新流程接管。
            catalog.setRichSyncedAt(now.minusDays(GameCatalogConstants.STALE_DAYS)
                    .minusMinutes(GameCatalogConstants.REFRESH_GRACE_MINUTES));
            catalog.setNextRefreshAt(now.minusMinutes(GameCatalogConstants.REFRESH_GRACE_MINUTES));
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
            catalog.setStatus(GameCatalogStatus.ENABLED.getCode());
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
                || lower.contains("capsule_184x69")
                || lower.contains("small_capsule")
                || lower.contains("/logo")
                || lower.contains("/icon");
    }

    private boolean hasBasicInfo(GameCatalog catalog) {
        return catalog != null
                && StringUtils.hasText(catalog.getSteamName())
                && StringUtils.hasText(catalog.getHeaderImage())
                && !isLowResolutionCover(catalog.getHeaderImage())
                && StringUtils.hasText(catalog.getNameZh())
                && StringUtils.hasText(catalog.getNameEn());
    }

    private SteamGameBasicPayload readBasicInfoCache(Long appId) {
        String cached = redisUtils.get(SteamRedisConstants.GAME_BASIC_INFO_KEY_PREFIX + appId);
        if (!StringUtils.hasText(cached)) {
            return null;
        }
        try {
            return objectMapper.readValue(cached, SteamGameBasicPayload.class);
        } catch (JsonProcessingException e) {
            log.warn("解析游戏基础信息缓存失败: appId={}", appId);
            redisUtils.del(SteamRedisConstants.GAME_BASIC_INFO_KEY_PREFIX + appId);
            return null;
        }
    }

    /** 将 Steam 基础信息 payload 写入 Redis，供并发请求复用。 */
    private void cacheBasicInfo(Long appId, SteamGameBasicPayload basic) {
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

    /** 保存或合并 Steam 基础信息，并保留后续懒更新的时间状态。 */
    private GameCatalog saveBasicInfo(GameCatalog existing, SteamGameBasicPayload basic) {
        LocalDateTime now = LocalDateTime.now();
        if (existing == null) {
            GameCatalog created = toCatalog(basic);
            created.setCreateTime(now);
            created.setUpdateTime(now);
            LocalDateTime pendingRefreshAt = now.minusDays(GameCatalogConstants.METRICS_TTL_DAYS)
                    .minusMinutes(GameCatalogConstants.REFRESH_GRACE_MINUTES);
            created.setMetricsSyncedAt(pendingRefreshAt);
            created.setPriceSyncedAt(pendingRefreshAt);
            created.setRichSyncedAt(now.minusDays(GameCatalogConstants.STALE_DAYS)
                    .minusMinutes(GameCatalogConstants.REFRESH_GRACE_MINUTES));
            created.setNextRefreshAt(now.minusMinutes(GameCatalogConstants.REFRESH_GRACE_MINUTES));
            gameCatalogMapper.insert(created);
            return created;
        }
        mergeBasicFields(existing, basic);
        if (!StringUtils.hasText(existing.getDisplayName())) {
            existing.setDisplayName(basic.getDisplayName());
        }
        existing.setStatus(existing.getStatus() == null ? 1 : existing.getStatus());
        existing.setSteamSyncedAt(now);
        existing.setStaticSyncedAt(now);
        existing.setLastRefreshAttemptAt(now);
        if (existing.getDetailReady() == null) {
            existing.setDetailReady(false);
        }
        existing.setUpdateTime(now);
        gameCatalogMapper.updateById(existing);
        return existing;
    }

    /** 将 Steam 基础信息 payload 转换为游戏目录实体。 */
    private GameCatalog toCatalog(SteamGameBasicPayload source) {
        GameCatalog target = new GameCatalog();
        mergeBasicFields(target, source);
        target.setStatus(GameCatalogStatus.ENABLED.getCode());
        target.setDetailReady(false);
        target.setRefreshStatus(GameCatalogRefreshStatus.BASIC_READY.getCode());
        target.setSteamSyncedAt(LocalDateTime.now());
        target.setStaticSyncedAt(LocalDateTime.now());
        target.setLastRefreshAttemptAt(LocalDateTime.now());
        return target;
    }

    /** 将 Steam 基础字段合并到游戏目录，不处理价格、评价等动态指标。 */
    private void mergeBasicFields(GameCatalog target, SteamGameBasicPayload source) {
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
        target.setSteamIsFree(source.getSteamIsFree());
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

    private GameDetailVO loadDetailFromStore(Long appId) {
        GameCatalog catalog = gameCatalogMapper.selectById(appId);
        if (catalog != null && Boolean.TRUE.equals(catalog.getDetailReady()) && !isStale(catalog)) {
            var richDetail = steamGameDetailService.find(appId);
            if (steamGameDetailService.needsRefresh(richDetail)) {
                // 外部 Steam 接口不再阻塞详情请求；先返回本地快照，后台完成后清缓存。
                steamGameDetailRefreshService.refresh(appId);
            }
            steamCatalogMetricsRefreshService.refreshIfStaleAsync(List.of(appId));
            return toDetailVO(catalog);
        }
        if (catalog != null && isStale(catalog)
                && Boolean.TRUE.equals(catalog.getDetailReady())) {
            // stale-while-revalidate：旧快照继续返回，后台成功后再替换。
            steamGameDetailRefreshService.refresh(appId);
            return toDetailVO(catalog);
        }
        if (catalog != null && !Boolean.TRUE.equals(catalog.getDetailReady())) {
            // 榜单/搜索已经入库的游戏先返回基础字段，避免首次进入同步等待 Steam 超时。
            steamGameDetailRefreshService.refresh(appId);
            steamCatalogMetricsRefreshService.refreshIfStaleAsync(List.of(appId));
            return toDetailVO(catalog);
        }
        if (catalog == null) {
            SteamGameDetailsPayload fetched = steamStoreClient.fetchAppDetails(appId);
            catalog = toCatalog(fetched);
            gameCatalogMapper.insert(catalog);
            try {
                steamGameDetailService.save(fetched);
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
                .eq(GameCatalog::getStatus, GameCatalogStatus.ENABLED.getCode());
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
                .eq(GameCatalog::getStatus, GameCatalogStatus.ENABLED.getCode())
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
        return steamChartClient.searchApps(query.getKeyword(), start, size).stream()
                .map(this::toListItemVO)
                .toList();
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

    /** 将 Steam 搜索或榜单 payload 转成游戏卡片 VO。 */
    private GameListItemVO toListItemVO(SteamChartGamePayload payload) {
        GameListItemVO vo = new GameListItemVO();
        vo.setAppId(payload.getAppId());
        vo.setName(payload.getName());
        vo.setCoverUrl(payload.getCoverUrl());
        vo.setPrice(toPriceVO(payload.getPrice()));
        return vo;
    }

    /** 将 Steam 价格 payload 转成卡片价格 VO。 */
    private GamePriceVO toPriceVO(SteamPricePayload payload) {
        if (payload == null) {
            return null;
        }
        GamePriceVO vo = new GamePriceVO();
        vo.setFree(payload.getFree());
        vo.setCurrency(payload.getCurrency());
        vo.setInitial(payload.getInitial());
        vo.setFinalPrice(payload.getFinalPrice());
        vo.setDiscountPercent(payload.getDiscountPercent());
        vo.setDiscountEndAt(payload.getDiscountEndAt());
        vo.setFormatted(payload.getFormatted());
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
            redisUtils.del(SteamRedisConstants.GAME_DETAIL_KEY_PREFIX + appId);
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

    private boolean isStale(GameCatalog catalog) {
        if (catalog.getSteamSyncedAt() == null) {
            return true;
        }
        return catalog.getSteamSyncedAt().isBefore(LocalDateTime.now().minusDays(GameCatalogConstants.STALE_DAYS));
    }

    /** 将 Mongo 富详情子文档合并到对外详情 VO。 */
    private void mergeRichDetail(GameDetailVO target, SteamGameDetail rich) {
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
            target.setScreenshots(rich.getScreenshots().stream().map(item -> {
                GameScreenshotVO screenshot = new GameScreenshotVO();
                screenshot.setFullUrl(item.getFullUrl());
                screenshot.setThumbnailUrl(item.getThumbnailUrl());
                return screenshot;
            }).toList());
        }
        if (rich.getMovies() != null && !rich.getMovies().isEmpty()) {
            target.setMovies(rich.getMovies().stream().map(item -> {
                GameMovieVO movie = new GameMovieVO();
                movie.setName(item.getName());
                movie.setThumbnailUrl(item.getThumbnailUrl());
                movie.setMp4Url(item.getMp4Url());
                movie.setWebmUrl(item.getWebmUrl());
                return movie;
            }).toList());
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
            target.setAchievementHighlights(normalizeAchievementIcons(rich.getAchievementHighlights().stream()
                    .map(item -> {
                        GameAchievementVO achievement = new GameAchievementVO();
                        achievement.setApiName(item.getApiName());
                        achievement.setName(item.getName());
                        achievement.setDescription(item.getDescription());
                        achievement.setIconUrl(item.getIconUrl());
                        achievement.setGlobalPercent(item.getGlobalPercent());
                        return achievement;
                    }).toList()));
        }
        if (StringUtils.hasText(rich.getPcRequirementsMin())) {
            target.setPcRequirementsMin(rich.getPcRequirementsMin());
        }
        if (StringUtils.hasText(rich.getPcRequirementsRec())) {
            target.setPcRequirementsRec(rich.getPcRequirementsRec());
        }
    }

    /** 输出已统一到新版资源路径的 Steam 成就图标地址。 */
    private List<GameAchievementVO> normalizeAchievementIcons(List<GameAchievementVO> achievements) {
        if (achievements == null || achievements.isEmpty()) {
            return achievements;
        }
        return achievements.stream().map(item -> {
            if (item == null || !StringUtils.hasText(item.getIconUrl())) {
                return item;
            }
            item.setIconUrl(SteamAchievementIconUrl.toCurrent(item.getIconUrl()));
            return item;
        }).toList();
    }

    /** 判断详情缓存是否缺少介绍、截图、视频或成就等关键富字段。 */
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

    /** 将 Steam 详情中的目录字段、价格和评价指标合并到已有实体。 */
    private void mergeSteamData(GameCatalog existing, SteamGameDetailsPayload fetched) {
        mergeBasicFields(existing, fetched);
        if (!StringUtils.hasText(existing.getDescSource())) {
            existing.setDescSource(GameCatalogConstants.DESC_SOURCE_COMMUNITY_FIRST);
        }
        if (existing.getStatus() == null) {
            existing.setStatus(GameCatalogStatus.ENABLED.getCode());
        }
        if (fetched.getSteamReviewScore() != null) {
            existing.setSteamReviewScore(fetched.getSteamReviewScore());
        }
        if (fetched.getSteamReviewCount() != null) {
            existing.setSteamReviewCount(fetched.getSteamReviewCount());
        }
        existing.setSteamIsFree(fetched.getSteamIsFree());
        existing.setPriceCurrency(fetched.getPriceCurrency());
        existing.setPriceInitial(fetched.getPriceInitial());
        existing.setPriceFinal(fetched.getPriceFinal());
        existing.setPriceDiscount(fetched.getPriceDiscount());
        existing.setPriceDiscountEndAt(fetched.getPriceDiscountEndAt());
        existing.setPriceFormatted(fetched.getPriceFormatted());
        LocalDateTime now = LocalDateTime.now();
        existing.setSteamSyncedAt(now);
        existing.setStaticSyncedAt(now);
        existing.setMetricsSyncedAt(now);
        existing.setPriceSyncedAt(now);
        existing.setRichSyncedAt(now);
        existing.setLastRefreshAttemptAt(now);
        existing.setNextRefreshAt(now.plusDays(7));
        existing.setRefreshStatus(GameCatalogRefreshStatus.READY.getCode());
        existing.setDetailReady(true);
    }

    /** 将 Steam 详情 payload 转成只包含关系库字段的目录实体。 */
    private GameCatalog toCatalog(SteamGameDetailsPayload source) {
        GameCatalog target = toCatalog((SteamGameBasicPayload) source);
        target.setDescSource(GameCatalogConstants.DESC_SOURCE_COMMUNITY_FIRST);
        target.setSteamReviewScore(source.getSteamReviewScore());
        target.setSteamReviewCount(source.getSteamReviewCount());
        target.setPriceCurrency(source.getPriceCurrency());
        target.setPriceInitial(source.getPriceInitial());
        target.setPriceFinal(source.getPriceFinal());
        target.setPriceDiscount(source.getPriceDiscount());
        target.setPriceDiscountEndAt(source.getPriceDiscountEndAt());
        target.setPriceFormatted(source.getPriceFormatted());
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

    /** 将关系库游戏目录转换为详情页基础 VO。 */
    private GameDetailVO toDetailVO(GameCatalog catalog) {
        GameDetailVO vo = new GameDetailVO();
        vo.setAppId(catalog.getAppId());
        vo.setName(StringUtils.hasText(catalog.getDisplayName()) ? catalog.getDisplayName() : catalog.getSteamName());
        vo.setShortDescription(GameCatalogConstants.DESC_SOURCE_COMMUNITY_FIRST.equalsIgnoreCase(catalog.getDescSource())
                ? catalog.getCommunityShort() : null);
        vo.setAboutHtml(GameCatalogConstants.DESC_SOURCE_COMMUNITY_FIRST.equalsIgnoreCase(catalog.getDescSource())
                ? catalog.getCommunityAbout() : null);
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
        vo.setPrice(buildPriceVO(catalog));
        vo.setSteamSyncedAt(catalog.getSteamSyncedAt());
        vo.setDetailReady(catalog.getDetailReady());
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

}
