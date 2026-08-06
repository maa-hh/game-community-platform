package com.game.community.steam.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.game.community.common.constant.steam.GameBoardConstants;
import com.game.community.common.constant.steam.SteamApiConstants;
import com.game.community.common.constant.steam.SteamRedisConstants;
import com.game.community.common.exception.BusinessException;
import com.game.community.steam.client.SteamStoreClient;
import com.game.community.steam.mapper.GameCatalogMapper;
import com.game.community.steam.mapper.GameChartSnapshotMapper;
import com.game.community.steam.service.GameCatalogService;
import com.game.community.steam.service.GameChartService;
import com.game.community.utils.RedisUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.community.model.entity.game.GameCatalog;
import com.game.community.model.entity.game.GameChartSnapshot;
import com.game.community.model.vo.game.GameChartItemVO;
import com.game.community.model.vo.game.GameListItemVO;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameChartServiceImpl implements GameChartService {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final WeekFields ISO_WEEK = WeekFields.ISO;

    private final SteamStoreClient steamStoreClient;
    private final GameCatalogService gameCatalogService;
    private final GameCatalogMapper gameCatalogMapper;
    private final GameChartSnapshotMapper gameChartSnapshotMapper;
    private final RedisUtils redisUtils;
    private final ObjectMapper objectMapper;

    @Resource(name = "steamChartExpansionExecutor")
    private Executor steamChartExpansionExecutor;

    private final Set<String> chartExpansionInProgress = ConcurrentHashMap.newKeySet();
    private final Set<String> chartExpansionExhausted = ConcurrentHashMap.newKeySet();

    @Override
    public List<GameChartItemVO> listChart(String board) {
        String resolvedBoard = resolveBoard(board);
        String cacheKey = SteamRedisConstants.GAME_CHART_KEY_PREFIX + resolvedBoard;
        String cached = redisUtils.get(cacheKey);
        if (StringUtils.hasText(cached)) {
            try {
                return objectMapper.readValue(cached, new TypeReference<List<GameChartItemVO>>() {});
            } catch (Exception e) {
                log.warn("解析游戏榜单缓存失败: board={}", resolvedBoard, e);
            }
        }
        String periodKey = resolveLatestPeriodKey(resolvedBoard);
        if (!StringUtils.hasText(periodKey)) {
            return List.of();
        }
        List<GameChartSnapshot> snapshots = gameChartSnapshotMapper.selectList(
                new LambdaQueryWrapper<GameChartSnapshot>()
                        .eq(GameChartSnapshot::getBoardType, resolvedBoard)
                        .eq(GameChartSnapshot::getPeriodKey, periodKey)
                        .orderByAsc(GameChartSnapshot::getRankNo));
        if (snapshots.isEmpty()) {
            return List.of();
        }
        List<Long> appIds = snapshots.stream().map(GameChartSnapshot::getAppId).toList();
        Map<Long, GameCatalog> catalogMap = loadCatalogMap(appIds);
        List<GameChartItemVO> result = new ArrayList<>();
        for (GameChartSnapshot snapshot : snapshots) {
            GameCatalog catalog = catalogMap.get(snapshot.getAppId());
            if (catalog == null || catalog.getStatus() != null && catalog.getStatus() != 1) {
                continue;
            }
            GameChartItemVO vo = toChartItemVO(catalog);
            vo.setRank(snapshot.getRankNo());
            result.add(vo);
        }
        try {
            redisUtils.set(cacheKey, objectMapper.writeValueAsString(result),
                    SteamRedisConstants.GAME_CHART_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("写入游戏榜单缓存失败: board={}", resolvedBoard, e);
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncChart(String board) {
        String resolvedBoard = resolveBoard(board);
        List<Long> appIds = steamStoreClient.fetchChartAppIds(resolvedBoard, SteamApiConstants.CHART_LIMIT);
        if (appIds.isEmpty()) {
            log.warn("Steam 榜单为空: board={}", resolvedBoard);
            return;
        }
        for (Long appId : appIds) {
            try {
                gameCatalogService.getDetail(appId);
            } catch (Exception e) {
                log.warn("同步游戏目录失败 appId={}", appId, e);
            }
        }
        String periodKey = currentDailyPeriodKey();
        LocalDateTime now = LocalDateTime.now(SHANGHAI);
        String snapshotId = UUID.randomUUID().toString().replace("-", "");
        gameChartSnapshotMapper.delete(new LambdaQueryWrapper<GameChartSnapshot>()
                .eq(GameChartSnapshot::getBoardType, resolvedBoard)
                .eq(GameChartSnapshot::getPeriodKey, periodKey));
        int rank = 1;
        for (Long appId : appIds) {
            GameChartSnapshot row = new GameChartSnapshot();
            row.setBoardType(resolvedBoard);
            row.setPeriodKey(periodKey);
            row.setAppId(appId);
            row.setRankNo(rank++);
            row.setSnapshotTime(now);
            row.setSnapshotId(snapshotId);
            row.setIsCurrent(1);
            gameChartSnapshotMapper.insert(row);
        }
        redisUtils.del(SteamRedisConstants.GAME_CHART_KEY_PREFIX + resolvedBoard);
        log.info("游戏榜单同步完成 board={} period={} count={}", resolvedBoard, periodKey, appIds.size());
    }

    @Override
    public void syncAllCharts() {
        for (String board : GameBoardConstants.CHART_BOARDS) {
            try {
                syncChart(board);
            } catch (Exception e) {
                log.error("游戏榜单同步失败 board={}", board, e);
            }
        }
    }

    @Override
    public void ensureChartPage(String board, long requiredCount) {
        String resolvedBoard = resolveBoard(board);
        long targetCount = Math.max(1L, requiredCount);
        String periodKey = resolveLatestPeriodKey(resolvedBoard);
        if (!StringUtils.hasText(periodKey)) {
            periodKey = currentDailyPeriodKey();
        }

        List<GameChartSnapshot> snapshots = currentSnapshots(resolvedBoard, periodKey);
        if (snapshots.size() >= targetCount) {
            return;
        }

        final String expansionPeriodKey = periodKey;
        final String expansionKey = resolvedBoard + ":" + expansionPeriodKey;
        if (chartExpansionExhausted.contains(expansionKey)) {
            return;
        }
        if (!chartExpansionInProgress.add(expansionKey)) {
            return;
        }
        try {
            CompletableFuture.runAsync(
                    () -> expandChartPage(resolvedBoard, expansionPeriodKey, targetCount),
                    steamChartExpansionExecutor)
                    .whenComplete((ignored, error) -> {
                        chartExpansionInProgress.remove(expansionKey);
                        if (error != null) {
                            log.warn("Steam 榜单后台扩展失败: board={}, period={}",
                                    resolvedBoard, expansionPeriodKey, error);
                        }
                    });
        } catch (RuntimeException e) {
            chartExpansionInProgress.remove(expansionKey);
            log.warn("提交 Steam 榜单后台扩展失败: board={}, period={}", resolvedBoard, periodKey, e);
        }
    }

    @Override
    public boolean isExpansionInProgress(String board) {
        String resolvedBoard = resolveBoard(board);
        String prefix = resolvedBoard + ":";
        return chartExpansionInProgress.stream().anyMatch(key -> key.startsWith(prefix));
    }

    private void expandChartPage(String resolvedBoard, String periodKey, long targetCount) {
        List<GameChartSnapshot> snapshots = currentSnapshots(resolvedBoard, periodKey);
        if (snapshots.size() >= targetCount) {
            return;
        }

        int start = snapshots.size();
        int limit = (int) Math.min(
                SteamApiConstants.CHART_LIMIT,
                Math.max(1L, targetCount - start));
        List<Long> appIds = steamStoreClient.fetchChartAppIds(resolvedBoard, start, limit);
        if (appIds.isEmpty()) {
            chartExpansionExhausted.add(resolvedBoard + ":" + periodKey);
            return;
        }

        Set<Long> existingIds = new HashSet<>(
                snapshots.stream().map(GameChartSnapshot::getAppId).toList());
        int nextRank = snapshots.stream()
                .map(GameChartSnapshot::getRankNo)
                .filter(java.util.Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(0) + 1;
        String snapshotId = snapshots.stream()
                .map(GameChartSnapshot::getSnapshotId)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElseGet(() -> UUID.randomUUID().toString().replace("-", ""));
        LocalDateTime now = LocalDateTime.now(SHANGHAI);

        int insertedCount = 0;
        for (Long appId : appIds) {
            if (appId == null || appId <= 0 || !existingIds.add(appId)) {
                continue;
            }
            GameChartSnapshot row = new GameChartSnapshot();
            row.setBoardType(resolvedBoard);
            row.setPeriodKey(periodKey);
            row.setAppId(appId);
            row.setRankNo(nextRank++);
            row.setSnapshotTime(now);
            row.setSnapshotId(snapshotId);
            row.setIsCurrent(1);
            gameChartSnapshotMapper.insert(row);
            insertedCount++;
            // 目录详情只在后台预热，不能阻塞榜单分页请求。
            gameCatalogService.warmup(appId);
        }
        if (insertedCount == 0) {
            chartExpansionExhausted.add(resolvedBoard + ":" + periodKey);
        }
        redisUtils.del(SteamRedisConstants.GAME_CHART_KEY_PREFIX + resolvedBoard);
    }

    @Override
    public boolean isAnyChartEmpty() {
        Long count = gameChartSnapshotMapper.selectCount(null);
        return count == null || count == 0;
    }

    private String resolveBoard(String board) {
        if (!StringUtils.hasText(board)) {
            throw new BusinessException("榜单类型无效");
        }
        String normalized = board.trim().toLowerCase();
        if (!GameBoardConstants.CHART_BOARDS.contains(normalized)) {
            throw new BusinessException("榜单类型无效");
        }
        return normalized;
    }

    private String resolveLatestPeriodKey(String board) {
        GameChartSnapshot latest = gameChartSnapshotMapper.selectOne(
                new LambdaQueryWrapper<GameChartSnapshot>()
                        .eq(GameChartSnapshot::getBoardType, board)
                        .orderByDesc(GameChartSnapshot::getSnapshotTime)
                        .orderByDesc(GameChartSnapshot::getId)
                        .last("LIMIT 1"));
        if (latest != null && StringUtils.hasText(latest.getPeriodKey())) {
            return latest.getPeriodKey();
        }
        return currentWeeklyPeriodKey();
    }

    private List<GameChartSnapshot> currentSnapshots(String board, String periodKey) {
        return gameChartSnapshotMapper.selectList(
                new LambdaQueryWrapper<GameChartSnapshot>()
                        .eq(GameChartSnapshot::getBoardType, board)
                        .eq(GameChartSnapshot::getPeriodKey, periodKey)
                        .orderByAsc(GameChartSnapshot::getRankNo));
    }

    private Map<Long, GameCatalog> loadCatalogMap(List<Long> appIds) {
        if (appIds.isEmpty()) {
            return Map.of();
        }
        List<GameCatalog> catalogs = gameCatalogMapper.selectBatchIds(appIds);
        Map<Long, GameCatalog> map = new LinkedHashMap<>();
        for (GameCatalog catalog : catalogs) {
            map.put(catalog.getAppId(), catalog);
        }
        return map;
    }

    private GameChartItemVO toChartItemVO(GameCatalog catalog) {
        GameListItemVO base = gameCatalogService.toListItem(catalog);
        GameChartItemVO vo = new GameChartItemVO();
        vo.setAppId(base.getAppId());
        vo.setName(base.getName());
        vo.setCoverUrl(base.getCoverUrl());
        vo.setAvgScore(base.getAvgScore());
        vo.setReviewCount(base.getReviewCount());
        vo.setDiscussCount(base.getDiscussCount());
        vo.setGenres(base.getGenres());
        vo.setSteamReviewScore(base.getSteamReviewScore());
        vo.setDeveloper(base.getDeveloper());
        vo.setPublisher(base.getPublisher());
        vo.setReleaseDate(base.getReleaseDate());
        vo.setPrice(base.getPrice());
        return vo;
    }

    static String currentWeeklyPeriodKey() {
        LocalDate today = LocalDate.now(SHANGHAI);
        int week = today.get(ISO_WEEK.weekOfWeekBasedYear());
        int year = today.get(ISO_WEEK.weekBasedYear());
        return String.format("%d-W%02d", year, week);
    }

    private String currentDailyPeriodKey() {
        return LocalDate.now(SHANGHAI).toString();
    }
}
