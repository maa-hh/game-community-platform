package com.game.community.recommend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.game.community.common.constant.RecommendConstants;
import com.game.community.common.constant.content.ContentConstants;
import com.game.community.common.constant.ApiErrorCodes;
import com.game.community.common.exception.BusinessException;
import com.game.community.common.util.HeatScoreCalculator;
import com.game.community.feign.ContentFeignClient;
import com.game.community.feign.SocialFeignClient;
import com.game.community.feign.UserFeignClient;
import com.game.community.model.base.Result;
import com.game.community.model.dto.recommend.ArticleRankScoreAgg;
import com.game.community.model.dto.recommend.HotRankQueryDTO;
import com.game.community.model.entity.recommend.HotRankSnapshot;
import com.game.community.model.enums.recommend.HotRankBoardType;
import com.game.community.model.message.ArticleBehaviorMessage;
import com.game.community.model.vo.article.ArticleListVO;
import com.game.community.model.vo.article.CategoryVO;
import com.game.community.model.vo.article.HotArticleVO;
import com.game.community.model.vo.social.ArticleStatsVO;
import com.game.community.model.vo.user.UserCardVO;
import com.game.community.recommend.mapper.HotRankSnapshotMapper;
import com.game.community.recommend.lock.RecommendLockService;
import com.game.community.recommend.service.HotRankBehaviorEventService;
import com.game.community.recommend.service.HotRankService;
import com.game.community.recommend.util.HotRankPeriodUtils;
import com.game.community.utils.RedisUtils;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.time.format.DateTimeParseException;
import org.springframework.beans.factory.annotation.Value;

@Slf4j
@Service
@RequiredArgsConstructor
public class HotRankServiceImpl implements HotRankService {

    private final RedisUtils redisUtils;
    private final HotRankSnapshotMapper hotRankSnapshotMapper;
    private final HotRankBehaviorEventService hotRankBehaviorEventService;
    private final ContentFeignClient contentFeignClient;
    private final SocialFeignClient socialFeignClient;
    private final UserFeignClient userFeignClient;
    private final RecommendLockService recommendLockService;

    @Resource(name = "hotRankRemoteExecutor")
    private Executor hotRankRemoteExecutor;

    private final AtomicBoolean totalBoardRebuildInProgress = new AtomicBoolean();

    @Value("${recommend.lock.rank-ttl-seconds:600}")
    private long rebuildLockTtlSeconds;
    private static final long CATEGORY_CACHE_TTL_MS = 5 * 60 * 1000L;
    private volatile Map<Long, String> categoryCache = Map.of();
    private volatile long categoryCacheExpiresAt;

    @Override
    public void applyBehaviorDelta(ArticleBehaviorMessage message) {
        if (message == null || message.getArticleId() == null) {
            return;
        }
        if (recommendLockService.isHeld(RecommendConstants.RANK_MAINTENANCE_LOCK_KEY)) {
            throw new IllegalStateException("热榜正在维护，等待维护完成后重试行为事件");
        }
        double scoreDelta = HeatScoreCalculator.deltaToScore(message);
        if (scoreDelta == 0D) {
            log.debug("行为事件计分为 0，跳过: articleId={}, message={}", message.getArticleId(), message);
            return;
        }
        log.debug("处理行为事件: articleId={}, scoreDelta={}", message.getArticleId(), scoreDelta);
        ArticleListVO article = loadArticle(message.getArticleId());
        if (article == null || !Objects.equals(article.getStatus(), ContentConstants.ArticleStatus.PUBLISHED)) {
            return;
        }
        // 1. 行为事件带时间戳落库（可回放聚合任意历史周期）
        if (!hotRankBehaviorEventService.saveEvent(message, scoreDelta)) {
            repairDuplicateProjection(message, article);
            return;
        }

        // 2. Redis 仅维护实时在榜：总榜 + 今天日榜 + 本周周榜
        String articleKey = article.getId().toString();
        LocalDate eventDate = HotRankPeriodUtils.eventDate(message);
        boolean isToday = HotRankPeriodUtils.isToday(eventDate);
        boolean isCurrentWeek = HotRankPeriodUtils.isCurrentWeek(eventDate);
        String dailyPeriodKey = HotRankPeriodUtils.dailyPeriodKey(eventDate);
        String weeklyPeriodKey = HotRankPeriodUtils.weeklyPeriodKey(eventDate);

        for (String scope : resolveScopes(article)) {
            incrementBoard(HotRankBoardType.TOTAL.getCode(), null, scope, articleKey, scoreDelta);

            if (isToday) {
                incrementBoard(HotRankBoardType.DAILY.getCode(), dailyPeriodKey, scope, articleKey, scoreDelta);
            }
            if (isCurrentWeek) {
                incrementBoard(HotRankBoardType.WEEKLY.getCode(), weeklyPeriodKey, scope, articleKey, scoreDelta);
            }
        }

    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rebuildTotalBoard() {
        withRebuildLock(this::rebuildTotalBoardInternal);
    }

    private void rebuildTotalBoardInternal() {
        String todayKey = HotRankPeriodUtils.dailyPeriodKey();
        log.info("开始从行为事件重建总榜（截止 {}）", todayKey);
        for (String scope : allScopes()) {
            List<ArticleRankScoreAgg> aggs = hotRankBehaviorEventService.aggregate(
                    HotRankBoardType.TOTAL, todayKey, scopeCategoryId(scope));
            writeAggScores(HotRankBoardType.TOTAL, todayKey, scope, aggs);
            persistSnapshotFromAggs(HotRankBoardType.TOTAL.getCode(), todayKey, scope, aggs);
        }
        log.info("总榜重建完成");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rebuildLivePeriodBoards() {
        withRebuildLock(this::rebuildLivePeriodBoardsInternal);
    }

    private void rebuildLivePeriodBoardsInternal() {
        String todayKey = HotRankPeriodUtils.dailyPeriodKey();
        String currentWeekKey = HotRankPeriodUtils.currentWeeklyPeriodKey();
        log.info("重建实时周期榜: daily={}, weekly={}", todayKey, currentWeekKey);
        for (String scope : allScopes()) {
            rebuildBoardFromEvents(HotRankBoardType.DAILY, todayKey, scope);
            rebuildBoardFromEvents(HotRankBoardType.WEEKLY, currentWeekKey, scope);
        }
    }

    private void rebuildBoardFromEvents(HotRankBoardType boardType, String periodKey, String scope) {
        refreshLiveBoardFromEventsInternal(boardType, periodKey, scope);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void finalizeWeeklyBoard() {
        withRebuildLock(this::finalizeWeeklyBoardInternal);
    }

    private void finalizeWeeklyBoardInternal() {
        String previousWeek = HotRankPeriodUtils.previousWeeklyPeriodKey();
        log.info("固化周榜快照: {}", previousWeek);
        for (String scope : allScopes()) {
            snapshotBoard(HotRankBoardType.WEEKLY.getCode(), previousWeek, scopeCategoryId(scope));
            redisUtils.del(liveRedisKey(HotRankBoardType.WEEKLY, previousWeek, scope));
        }
        String currentWeek = HotRankPeriodUtils.currentWeeklyPeriodKey();
        log.info("清空本周周榜累积 Key: {}", currentWeek);
        for (String scope : allScopes()) {
            redisUtils.del(liveRedisKey(HotRankBoardType.WEEKLY, currentWeek, scope));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void finalizeDailyBoard(LocalDate date) {
        withRebuildLock(() -> finalizeDailyBoardInternal(date));
    }

    private void finalizeDailyBoardInternal(LocalDate date) {
        LocalDate target = date == null ? HotRankPeriodUtils.today().minusDays(1) : date;
        String periodKey = HotRankPeriodUtils.dailyPeriodKey(target);
        log.info("固化日榜快照: {}", periodKey);
        for (String scope : allScopes()) {
            snapshotBoard(HotRankBoardType.DAILY.getCode(), periodKey, scopeCategoryId(scope));
            redisUtils.del(liveRedisKey(HotRankBoardType.DAILY, periodKey, scope));
        }
    }

    @Override
    public List<HotArticleVO> listRank(HotRankQueryDTO query, Long userId) {
        HotRankQueryDTO normalized = query == null ? new HotRankQueryDTO() : query;
        HotRankBoardType boardType;
        try {
            boardType = HotRankBoardType.fromCode(normalized.getBoard());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ApiErrorCodes.BAD_REQUEST, "不支持的热榜类型");
        }
        Long categoryId = normalized.getCategoryId();
        String periodKey = normalized.getPeriodKey();
        boolean refresh = normalized.isRefresh();
        String scope = RecommendConstants.categoryScope(categoryId);
        String resolvedPeriod = resolvePeriodKey(boardType, periodKey);

        if (boardType == HotRankBoardType.DAILY) {
            if (HotRankPeriodUtils.isToday(resolvedPeriod)) {
                if (refresh) {
                    refreshIfAllowed(boardType, resolvedPeriod, scope);
                } else {
                    ensureLiveBoard(boardType, categoryId, resolvedPeriod);
                }
                return listFromRedis(liveRedisKey(boardType, resolvedPeriod, scope), userId,
                        boardType.getCode(), resolvedPeriod, categoryId);
            }
            return listHistoricalRank(boardType, resolvedPeriod, scope, userId);
        }

        if (boardType == HotRankBoardType.WEEKLY) {
            if (HotRankPeriodUtils.isCurrentWeek(resolvedPeriod)) {
                if (refresh) {
                    refreshIfAllowed(boardType, resolvedPeriod, scope);
                } else {
                    ensureLiveBoard(boardType, categoryId, resolvedPeriod);
                }
                return listFromRedis(liveRedisKey(boardType, resolvedPeriod, scope), userId,
                        boardType.getCode(), resolvedPeriod, categoryId);
            }
            return listHistoricalRank(boardType, resolvedPeriod, scope, userId);
        }

        // 总榜：选定日期截止的累计热度；仅「今天」走实时 Redis
        if (HotRankPeriodUtils.isToday(resolvedPeriod)) {
            if (refresh) {
                refreshIfAllowed(boardType, resolvedPeriod, scope);
            }
            ensureLiveBoard(boardType, categoryId, resolvedPeriod);
            return listFromRedis(liveRedisKey(boardType, resolvedPeriod, scope), userId,
                    boardType.getCode(), resolvedPeriod, categoryId);
        }
        return listHistoricalRank(boardType, resolvedPeriod, scope, userId);
    }

    private List<HotArticleVO> listHistoricalRank(HotRankBoardType boardType,
                                                String periodKey,
                                                String scope,
                                                Long userId) {
        String passiveKey = RecommendConstants.passiveCacheKey(boardType.getCode(), periodKey, scope);
        if (redisUtils.zSize(passiveKey) > 0) {
            return listFromRedis(passiveKey, userId, boardType.getCode(), periodKey, scopeCategoryId(scope));
        }
        // 日/周榜=周期内新增热度；总榜=截止 periodKey 当日累计；均以行为事件聚合为准
        List<ArticleRankScoreAgg> aggs = hotRankBehaviorEventService.aggregate(boardType, periodKey, scopeCategoryId(scope));
        if (aggs.isEmpty()) {
            return List.of();
        }
        persistSnapshotFromAggs(boardType.getCode(), periodKey, scope, aggs);
        warmPassiveCacheFromAggs(boardType.getCode(), periodKey, scope, aggs);
        return listFromSnapshot(boardType.getCode(), periodKey, scope, userId);
    }

    private List<HotArticleVO> listFromRedis(String key, Long userId, String boardType,
                                             String periodKey, Long categoryId) {
        Map<String, Double> rankedScores = redisUtils.zReverseRangeWithScores(key, 0, RecommendConstants.HOT_RANK_SIZE - 1);
        if (rankedScores.isEmpty()) {
            return List.of();
        }
        return buildRankedList(key, rankedScores, userId, boardType, periodKey, categoryId);
    }

    private List<HotArticleVO> listFromSnapshot(String boardType, String periodKey, String scope, Long userId) {
        List<HotRankSnapshot> rows = hotRankSnapshotMapper.selectList(new LambdaQueryWrapper<HotRankSnapshot>()
                .eq(HotRankSnapshot::getBoardType, boardType)
                .eq(HotRankSnapshot::getPeriodKey, periodKey)
                .eq(HotRankSnapshot::getCategoryScope, scope)
                .orderByAsc(HotRankSnapshot::getRankNo)
                .last("LIMIT " + RecommendConstants.HOT_RANK_SIZE));
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> articleIds = rows.stream().map(HotRankSnapshot::getArticleId).toList();
        Map<Long, Double> scoreMap = rows.stream()
                .collect(Collectors.toMap(HotRankSnapshot::getArticleId, HotRankSnapshot::getHotScore, (a, b) -> a, LinkedHashMap::new));
        Map<Integer, Long> rankMap = rows.stream()
                .collect(Collectors.toMap(HotRankSnapshot::getRankNo, HotRankSnapshot::getArticleId, (a, b) -> a));
        RankEnrichment enrichment = enrich(articleIds, userId);
        Map<Long, ArticleListVO> articleMap = enrichment.articles().stream()
                .collect(Collectors.toMap(ArticleListVO::getId, Function.identity(), (a, b) -> a));
        List<HotArticleVO> result = new ArrayList<>();
        int displayRank = 1;
        for (int rank = 1; rank <= rows.size(); rank++) {
            Long articleId = rankMap.get(rank);
            ArticleListVO article = articleMap.get(articleId);
            if (article == null || !Objects.equals(article.getStatus(), ContentConstants.ArticleStatus.PUBLISHED)
                    || !belongsToCategory(article, scopeCategoryId(scope))) {
                if (article == null || !belongsToCategory(article, scopeCategoryId(scope))) {
                    hotRankSnapshotMapper.deleteById(rows.get(rank - 1).getId());
                }
                continue;
            }
            UserCardVO author = article.getAuthorAccountId() == null
                    ? null
                    : enrichment.users().get(article.getAuthorAccountId());
            result.add(toVO(article,
                    enrichment.stats().getOrDefault(articleId, emptyStats(articleId)),
                    author,
                    enrichment.categoryNames(),
                    scoreMap.get(articleId),
                    displayRank++,
                    boardType,
                    periodKey));
        }
        return result;
    }

    private List<HotArticleVO> buildRankedList(String redisKey,
                                               Map<String, Double> rankedScores,
                                               Long userId,
                                               String boardType,
                                               String periodKey,
                                               Long categoryId) {
        List<Long> articleIds = rankedScores.keySet().stream().map(this::parseLong).filter(Objects::nonNull).toList();
        RankEnrichment enrichment = enrich(articleIds, userId);
        Map<Long, ArticleListVO> articleMap = enrichment.articles().stream()
                .collect(Collectors.toMap(ArticleListVO::getId, Function.identity(), (a, b) -> a));

        List<HotArticleVO> result = new ArrayList<>();
        int rank = 1;
        for (Long articleId : articleIds) {
            ArticleListVO article = articleMap.get(articleId);
            if (article == null || !Objects.equals(article.getStatus(), ContentConstants.ArticleStatus.PUBLISHED)) {
                redisUtils.zRemove(redisKey, articleId.toString());
                continue;
            }
            if (!belongsToCategory(article, categoryId)) {
                redisUtils.zRemove(redisKey, articleId.toString());
                continue;
            }
            Double score = rankedScores.get(articleId.toString());
            if (score == null || score <= 0D) {
                continue;
            }
            UserCardVO author = article.getAuthorAccountId() == null
                    ? null
                    : enrichment.users().get(article.getAuthorAccountId());
            result.add(toVO(article,
                    enrichment.stats().getOrDefault(articleId, emptyStats(articleId)),
                    author,
                    enrichment.categoryNames(),
                    score,
                    rank++,
                    boardType,
                    periodKey));
            if (rank > RecommendConstants.HOT_RANK_SIZE) {
                break;
            }
        }
        return result;
    }

    private boolean belongsToCategory(ArticleListVO article, Long categoryId) {
        if (categoryId == null) {
            return true;
        }
        if (Objects.equals(article.getCategoryId(), categoryId)) {
            return true;
        }
        return article.getCategoryIds() != null && article.getCategoryIds().contains(categoryId);
    }

    private void incrementBoard(String board, String periodSegment, String scope, String articleKey, double scoreDelta) {
        String key = liveRedisKey(HotRankBoardType.fromCode(board), periodKeyForBoard(board, periodSegment), scope);
        boolean updated = redisUtils.zIncrementScoreAndTrimUnlessLocked(
                key,
                RecommendConstants.RANK_MAINTENANCE_LOCK_KEY,
                articleKey,
                scoreDelta,
                RecommendConstants.HOT_RANK_SIZE);
        if (!updated) {
            throw new IllegalStateException("热榜正在重建，等待重建完成后重试行为事件");
        }
    }

    private void snapshotBoard(String boardType, String periodKey, Long categoryId) {
        String scope = RecommendConstants.categoryScope(categoryId);
        String redisKey = liveRedisKey(HotRankBoardType.fromCode(boardType), periodKey, scope);
        Set<String> rankedIds = redisUtils.zReverseRange(redisKey, 0, RecommendConstants.HOT_RANK_SIZE - 1);
        if (rankedIds.isEmpty()) {
            List<ArticleRankScoreAgg> aggs = hotRankBehaviorEventService.aggregate(
                    HotRankBoardType.fromCode(boardType), periodKey, categoryId);
            if (aggs.isEmpty()) {
                deleteSnapshot(boardType, periodKey, scope);
                return;
            }
            persistSnapshotFromAggs(boardType, periodKey, scope, aggs);
            return;
        }
        hotRankSnapshotMapper.delete(new LambdaQueryWrapper<HotRankSnapshot>()
                .eq(HotRankSnapshot::getBoardType, boardType)
                .eq(HotRankSnapshot::getPeriodKey, periodKey)
                .eq(HotRankSnapshot::getCategoryScope, scope));

        LocalDateTime now = LocalDateTime.now();
        int rank = 1;
        for (String articleId : rankedIds) {
            Double score = redisUtils.zScore(redisKey, articleId);
            HotRankSnapshot row = new HotRankSnapshot();
            row.setBoardType(boardType);
            row.setPeriodKey(periodKey);
            row.setCategoryScope(scope);
            row.setArticleId(Long.valueOf(articleId));
            row.setRankNo(rank++);
            row.setHotScore(score == null ? 0D : score);
            row.setSnapshotTime(now);
            hotRankSnapshotMapper.insert(row);
        }
    }

    private void persistSnapshotFromAggs(String boardType, String periodKey, String scope, List<ArticleRankScoreAgg> aggs) {
        deleteSnapshot(boardType, periodKey, scope);
        if (aggs.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        int rank = 1;
        for (ArticleRankScoreAgg agg : aggs) {
            HotRankSnapshot row = new HotRankSnapshot();
            row.setBoardType(boardType);
            row.setPeriodKey(periodKey);
            row.setCategoryScope(scope);
            row.setArticleId(agg.getArticleId());
            row.setRankNo(rank++);
            row.setHotScore(agg.getTotalScore() == null ? 0D : agg.getTotalScore());
            row.setSnapshotTime(now);
            hotRankSnapshotMapper.insert(row);
        }
    }

    private void deleteSnapshot(String boardType, String periodKey, String scope) {
        hotRankSnapshotMapper.delete(new LambdaQueryWrapper<HotRankSnapshot>()
                .eq(HotRankSnapshot::getBoardType, boardType)
                .eq(HotRankSnapshot::getPeriodKey, periodKey)
                .eq(HotRankSnapshot::getCategoryScope, scope));
    }

    private void withRebuildLock(Runnable action) {
        boolean acquired = recommendLockService.tryExecute(
                RecommendConstants.RANK_MAINTENANCE_LOCK_KEY, rebuildLockTtlSeconds, action);
        if (!acquired) {
            log.warn("热榜维护任务正在其他实例执行，跳过本次任务");
        }
    }

    private void writeAggScores(HotRankBoardType boardType, String periodKey, String scope, List<ArticleRankScoreAgg> aggs) {
        String key = liveRedisKey(boardType, periodKey, scope);
        Map<String, Double> scores = new LinkedHashMap<>();
        for (ArticleRankScoreAgg agg : aggs) {
            if (agg.getTotalScore() == null || agg.getTotalScore() <= 0D) {
                continue;
            }
            scores.put(agg.getArticleId().toString(), agg.getTotalScore());
        }
        redisUtils.zReplace(key, scores, RecommendConstants.HOT_RANK_SIZE);
    }

    private void refreshLiveBoardFromEvents(HotRankBoardType boardType, String periodKey, String scope) {
        withRebuildLock(() -> refreshLiveBoardFromEventsInternal(boardType, periodKey, scope));
    }

    private void refreshLiveBoardFromEventsInternal(HotRankBoardType boardType, String periodKey, String scope) {
        List<ArticleRankScoreAgg> aggs = hotRankBehaviorEventService.aggregate(
                boardType, periodKey, scopeCategoryId(scope));
        writeAggScores(boardType, periodKey, scope, aggs);
    }

    /** refresh 参数只触发受冷却保护的后台重建，避免公开接口被反复打到同步 SQL。 */
    private void refreshIfAllowed(HotRankBoardType boardType, String periodKey, String scope) {
        String key = RecommendConstants.REFRESH_COOLDOWN_KEY_PREFIX
                + boardType.getCode() + ":" + periodKey + ":" + scope;
        if (Boolean.TRUE.equals(redisUtils.setIfAbsent(key, "1", RecommendConstants.REFRESH_COOLDOWN_SECONDS))) {
            refreshLiveBoardFromEvents(boardType, periodKey, scope);
        } else {
            log.debug("热榜刷新仍在冷却窗口，复用现有 Redis 投影: key={}", key);
        }
    }

    private void warmPassiveCacheFromAggs(String boardType, String periodKey, String scope, List<ArticleRankScoreAgg> aggs) {
        if (aggs.isEmpty()) {
            return;
        }
        String cacheKey = RecommendConstants.passiveCacheKey(boardType, periodKey, scope);
        Map<String, Double> scores = new LinkedHashMap<>();
        for (ArticleRankScoreAgg agg : aggs) {
            if (agg.getTotalScore() != null && agg.getTotalScore() > 0D) {
                scores.put(agg.getArticleId().toString(), agg.getTotalScore());
            }
        }
        redisUtils.zReplace(cacheKey, scores, RecommendConstants.HOT_RANK_SIZE);
        redisUtils.expire(cacheKey, RecommendConstants.PASSIVE_CACHE_TTL_SECONDS);
    }

    /** 只在实时投影为空时回源；总榜重建较重，周期榜直接按行为事件刷新。 */
    private void ensureLiveBoard(HotRankBoardType boardType, Long categoryId, String periodKey) {
        String scope = RecommendConstants.categoryScope(categoryId);
        String key = liveRedisKey(boardType, periodKey, scope);
        if (redisUtils.zSize(key) != 0) {
            return;
        }
        if (boardType == HotRankBoardType.TOTAL) {
            if (totalBoardRebuildInProgress.compareAndSet(false, true)) {
                CompletableFuture.runAsync(() -> {
                    try {
                        log.info("总榜 Redis 为空，后台启动总榜重建");
                        rebuildTotalBoard();
                    } finally {
                        totalBoardRebuildInProgress.set(false);
                    }
                }, hotRankRemoteExecutor);
            }
            return;
        }
        refreshLiveBoardFromEvents(boardType, periodKey, scope);
    }

    private List<String> allScopes() {
        List<String> scopes = new ArrayList<>();
        scopes.add(RecommendConstants.CATEGORY_SCOPE_ALL);
        for (Long categoryId : categoryNameMap().keySet()) {
            scopes.add(RecommendConstants.categoryScope(categoryId));
        }
        return scopes;
    }

    private Long scopeCategoryId(String scope) {
        if (RecommendConstants.CATEGORY_SCOPE_ALL.equals(scope)) {
            return null;
        }
        if (scope.startsWith(RecommendConstants.CATEGORY_SCOPE_PREFIX)) {
            return Long.valueOf(scope.substring(RecommendConstants.CATEGORY_SCOPE_PREFIX.length()));
        }
        return null;
    }

    private List<String> resolveScopes(ArticleListVO article) {
        Set<String> scopes = new LinkedHashSet<>();
        scopes.add(RecommendConstants.CATEGORY_SCOPE_ALL);
        List<Long> categoryIds = article.getCategoryIds();
        if (CollectionUtils.isEmpty(categoryIds) && article.getCategoryId() != null) {
            categoryIds = List.of(article.getCategoryId());
        }
        if (!CollectionUtils.isEmpty(categoryIds)) {
            for (Long categoryId : categoryIds) {
                scopes.add(RecommendConstants.categoryScope(categoryId));
            }
        }
        return new ArrayList<>(scopes);
    }

    private String liveRedisKey(HotRankBoardType boardType, String periodKey, String scope) {
        return switch (boardType) {
            case TOTAL -> RecommendConstants.RANK_KEY_PREFIX + boardType.getCode() + ":" + scope;
            case WEEKLY, DAILY -> {
                String segment = boardType == HotRankBoardType.DAILY
                        ? HotRankPeriodUtils.dailyRedisSegment(HotRankPeriodUtils.parseDailyPeriodKey(
                        periodKey == null ? HotRankPeriodUtils.dailyPeriodKey() : periodKey))
                        : (periodKey == null ? HotRankPeriodUtils.weeklyPeriodKey() : periodKey);
                yield RecommendConstants.RANK_KEY_PREFIX + boardType.getCode() + ":" + segment + ":" + scope;
            }
        };
    }

    private String periodKeyForBoard(String board, String periodSegment) {
        if (HotRankBoardType.TOTAL.getCode().equals(board)) {
            return HotRankPeriodUtils.dailyPeriodKey();
        }
        if (HotRankBoardType.DAILY.getCode().equals(board)) {
            if (periodSegment == null) {
                return HotRankPeriodUtils.dailyPeriodKey();
            }
            if (periodSegment.contains("-")) {
                return periodSegment;
            }
            LocalDate date = LocalDate.parse(periodSegment, java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
            return HotRankPeriodUtils.dailyPeriodKey(date);
        }
        return periodSegment == null ? HotRankPeriodUtils.currentWeeklyPeriodKey() : periodSegment;
    }

    private String defaultPeriodKey(HotRankBoardType boardType) {
        return switch (boardType) {
            case TOTAL -> HotRankPeriodUtils.dailyPeriodKey();
            case WEEKLY -> HotRankPeriodUtils.defaultWeeklyDisplayPeriodKey();
            case DAILY -> HotRankPeriodUtils.dailyPeriodKey();
        };
    }

    private String resolvePeriodKey(HotRankBoardType boardType, String periodKey) {
        if (periodKey == null || periodKey.isBlank()) {
            return defaultPeriodKey(boardType);
        }
        String normalized = periodKey.trim();
        try {
            if (boardType == HotRankBoardType.WEEKLY) {
                HotRankPeriodUtils.parseWeeklyPeriodKey(normalized);
            } else {
                HotRankPeriodUtils.parseDailyPeriodKey(normalized);
            }
            return normalized;
        } catch (DateTimeParseException | IllegalArgumentException e) {
            throw new BusinessException(ApiErrorCodes.BAD_REQUEST, "热榜周期格式不正确");
        }
    }

    /** 幂等事件已落库但 Redis 更新中断时，按事件表精确重算当前投影，保证 Kafka 重试可自愈。 */
    private void repairDuplicateProjection(ArticleBehaviorMessage message, ArticleListVO article) {
        boolean repaired = recommendLockService.tryExecute(
                RecommendConstants.RANK_MAINTENANCE_LOCK_KEY,
                rebuildLockTtlSeconds,
                () -> {
                    LocalDate today = HotRankPeriodUtils.today();
                    LocalDateTime totalStart = LocalDateTime.of(1970, 1, 1, 0, 0);
                    LocalDateTime totalEnd = today.plusDays(1).atStartOfDay();
                    LocalDate eventDate = HotRankPeriodUtils.eventDate(message);
                    boolean isToday = HotRankPeriodUtils.isToday(eventDate);
                    boolean isCurrentWeek = HotRankPeriodUtils.isCurrentWeek(eventDate);
                    String articleKey = article.getId().toString();
                    for (String scope : resolveScopes(article)) {
                        double totalScore = hotRankBehaviorEventService.sumArticleScore(
                                article.getId(), totalStart, totalEnd);
                        redisUtils.zSetScoreAndTrim(liveRedisKey(HotRankBoardType.TOTAL, null, scope),
                                articleKey, totalScore, RecommendConstants.HOT_RANK_SIZE);
                        if (isToday) {
                            LocalDateTime start = eventDate.atStartOfDay();
                            redisUtils.zSetScoreAndTrim(
                                    liveRedisKey(HotRankBoardType.DAILY,
                                            HotRankPeriodUtils.dailyPeriodKey(eventDate), scope),
                                    articleKey,
                                    hotRankBehaviorEventService.sumArticleScore(article.getId(), start, start.plusDays(1)),
                                    RecommendConstants.HOT_RANK_SIZE);
                        }
                        if (isCurrentWeek) {
                            LocalDate weekStart = eventDate.minusDays(eventDate.getDayOfWeek().getValue() - 1L);
                            redisUtils.zSetScoreAndTrim(
                                    liveRedisKey(HotRankBoardType.WEEKLY,
                                            HotRankPeriodUtils.weeklyPeriodKey(eventDate), scope),
                                    articleKey,
                                    hotRankBehaviorEventService.sumArticleScore(article.getId(),
                                            weekStart.atStartOfDay(), weekStart.plusWeeks(1).atStartOfDay()),
                                    RecommendConstants.HOT_RANK_SIZE);
                        }
                    }
                    log.info("重复行为事件已修复热榜投影: eventId={}, articleId={}",
                            message.getEventId(), article.getId());
                });
        if (!repaired) {
            throw new IllegalStateException("热榜正在维护，等待维护完成后重试重复行为事件");
        }
    }

    private double statsToScore(ArticleStatsVO stats) {
        return defaultLong(stats.getViewCount()) * RecommendConstants.VIEW_WEIGHT
                + defaultLong(stats.getLikeCount()) * RecommendConstants.LIKE_WEIGHT
                + defaultLong(stats.getFavoriteCount()) * RecommendConstants.FAVORITE_WEIGHT
                + defaultLong(stats.getShareCount()) * RecommendConstants.SHARE_WEIGHT
                + (defaultLong(stats.getCommentCount()) + defaultLong(stats.getReplyCount())) * RecommendConstants.COMMENT_WEIGHT;
    }

    private HotArticleVO toVO(ArticleListVO article,
                                ArticleStatsVO stats,
                                UserCardVO author,
                                Map<Long, String> categoryNameMap,
                                Double score,
                                int rank,
                                String boardType,
                                String periodKey) {
        HotArticleVO vo = new HotArticleVO();
        vo.setRank(rank);
        vo.setId(article.getId());
        vo.setPublicId(article.getPublicId());
        vo.setAuthorAccountId(article.getAuthorAccountId());
        vo.setTitle(article.getTitle());
        vo.setSummary(article.getSummary());
        vo.setCoverUrl(article.getCoverUrl());
        vo.setPostType(article.getPostType());
        vo.setRefArticleId(article.getRefArticleId());
        vo.setVideoUrl(article.getVideoUrl());
        vo.setCategoryId(article.getCategoryId());
        vo.setCategoryIds(article.getCategoryIds());
        if (!CollectionUtils.isEmpty(article.getCategoryNames())) {
            vo.setCategoryNames(article.getCategoryNames());
            vo.setCategoryName(article.getCategoryNames().get(0));
        } else if (article.getCategoryId() != null) {
            vo.setCategoryName(categoryNameMap.get(article.getCategoryId()));
        }
        vo.setBoardType(boardType);
        vo.setPeriodKey(periodKey);
        vo.setAuthorName(author == null ? null : author.getUsername());
        vo.setAuthorAvatar(author == null ? null : author.getAvatar());
        vo.setLikeCount(defaultLong(stats.getLikeCount()));
        vo.setCommentCount(defaultLong(stats.getCommentCount()));
        vo.setCommentLikeCount(defaultLong(stats.getCommentLikeCount()));
        vo.setReplyCount(defaultLong(stats.getReplyCount()));
        vo.setReplyLikeCount(defaultLong(stats.getReplyLikeCount()));
        vo.setViewCount(defaultLong(stats.getViewCount()));
        vo.setLiked(Boolean.TRUE.equals(stats.getLiked()));
        vo.setHotScore(score == null ? statsToScore(stats) : score);
        vo.setPublishedTime(article.getPublishedTime());
        vo.setCreateTime(article.getCreateTime());
        vo.setUpdateTime(article.getUpdateTime());
        return vo;
    }

    private ArticleListVO loadArticle(Long articleId) {
        Result<List<ArticleListVO>> response = contentFeignClient.listArticlesByIds(List.of(articleId));
        if (response == null || response.getCode() == null || response.getCode() != 200) {
            throw new IllegalStateException("content-service 查询文章失败，等待 Kafka 重试");
        }
        List<ArticleListVO> articles = safeList(response.getData());
        return articles.stream().filter(item -> Objects.equals(item.getId(), articleId)).findFirst().orElse(null);
    }

    private Map<Long, ArticleStatsVO> statsMap(Long userId, List<Long> articleIds) {
        if (CollectionUtils.isEmpty(articleIds)) {
            return Map.of();
        }
        List<ArticleStatsVO> stats = safeList(unwrap(socialFeignClient.getStats(new ArrayList<>(new LinkedHashSet<>(articleIds)), userId)));
        return stats.stream().collect(Collectors.toMap(ArticleStatsVO::getArticleId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
    }

    private Map<Long, UserCardVO> userMapByAccountId(List<Long> accountIds) {
        if (CollectionUtils.isEmpty(accountIds)) {
            return Map.of();
        }
        List<UserCardVO> users = safeList(unwrap(userFeignClient.getUsersByAccountIds(new ArrayList<>(new LinkedHashSet<>(accountIds)))));
        return users.stream().collect(Collectors.toMap(UserCardVO::getAccountId, Function.identity(), (a, b) -> a));
    }

    private Map<Long, String> categoryNameMap() {
        long now = System.currentTimeMillis();
        Map<Long, String> cached = categoryCache;
        if (now < categoryCacheExpiresAt) {
            return cached;
        }
        synchronized (this) {
            now = System.currentTimeMillis();
            if (now < categoryCacheExpiresAt) {
                return categoryCache;
            }
            List<CategoryVO> categories = enabledCategories();
            if (!categories.isEmpty()) {
                Map<Long, String> refreshed = new HashMap<>();
                for (CategoryVO category : categories) {
                    refreshed.put(category.getId(), category.getName());
                }
                categoryCache = Map.copyOf(refreshed);
                categoryCacheExpiresAt = now + CATEGORY_CACHE_TTL_MS;
            } else if (!categoryCache.isEmpty()) {
                // 下游暂时不可用时继续使用上一次成功的分类缓存。
                categoryCacheExpiresAt = now + 30_000L;
            }
            return categoryCache;
        }
    }

    private RankEnrichment enrich(List<Long> articleIds, Long userId) {
        // 文章、统计、分类互不依赖，三路并行；用户信息等待文章返回后再按作者批量查询。
        CompletableFuture<List<ArticleListVO>> articlesFuture = CompletableFuture.supplyAsync(
                () -> safeList(unwrap(contentFeignClient.listArticlesByIds(articleIds))), hotRankRemoteExecutor);
        CompletableFuture<Map<Long, ArticleStatsVO>> statsFuture = CompletableFuture.supplyAsync(
                () -> statsMap(userId, articleIds), hotRankRemoteExecutor);
        CompletableFuture<Map<Long, String>> categoriesFuture = CompletableFuture.supplyAsync(
                this::categoryNameMap, hotRankRemoteExecutor);

        List<ArticleListVO> articles = joinOrDefault(articlesFuture, List.of());
        CompletableFuture<Map<Long, UserCardVO>> usersFuture = CompletableFuture.supplyAsync(
                () -> userMapByAccountId(articles.stream()
                        .map(ArticleListVO::getAuthorAccountId)
                        .filter(Objects::nonNull)
                        .toList()), hotRankRemoteExecutor);

        return new RankEnrichment(
                articles,
                joinOrDefault(statsFuture, Map.of()),
                joinOrDefault(usersFuture, Map.of()),
                joinOrDefault(categoriesFuture, Map.of()));
    }

    private <T> T joinOrDefault(CompletableFuture<T> future, T fallback) {
        try {
            return future.join();
        } catch (CompletionException e) {
            log.warn("榜单远程数据加载失败，使用降级数据: {}",
                    e.getCause() == null ? e.getMessage() : e.getCause().toString());
            return fallback;
        }
    }

    private record RankEnrichment(
            List<ArticleListVO> articles,
            Map<Long, ArticleStatsVO> stats,
            Map<Long, UserCardVO> users,
            Map<Long, String> categoryNames) {
    }

    private List<CategoryVO> enabledCategories() {
        return safeList(unwrap(contentFeignClient.listEnabledCategories()));
    }

    private ArticleStatsVO emptyStats(Long articleId) {
        ArticleStatsVO vo = new ArticleStatsVO();
        vo.setArticleId(articleId);
        vo.setLikeCount(0L);
        vo.setCommentCount(0L);
        vo.setCommentLikeCount(0L);
        vo.setReplyCount(0L);
        vo.setReplyLikeCount(0L);
        vo.setViewCount(0L);
        vo.setFavoriteCount(0L);
        vo.setShareCount(0L);
        vo.setLiked(false);
        vo.setFavorited(false);
        return vo;
    }

    private <T> T unwrap(Result<T> result) {
        return result == null || result.getCode() == null || result.getCode() != 200 ? null : result.getData();
    }

    private <T> List<T> safeList(List<T> list) {
        return list == null ? Collections.emptyList() : list;
    }

    private Long parseLong(String value) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private long defaultLong(Long value) {
        return value == null ? 0L : value;
    }
}
