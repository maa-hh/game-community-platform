package com.game.community.steam.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.community.common.constant.steam.GameBoardConstants;
import com.game.community.common.constant.steam.SteamApiConstants;
import com.game.community.common.constant.steam.SteamRedisConstants;
import com.game.community.common.exception.BusinessException;
import com.game.community.model.dto.game.GameChartQuery;
import com.game.community.model.entity.game.GameCatalog;
import com.game.community.model.entity.game.GameChartSnapshot;
import com.game.community.model.payload.steam.SteamChartGamePayload;
import com.game.community.model.payload.steam.SteamPricePayload;
import com.game.community.model.vo.game.GameChartItemVO;
import com.game.community.model.vo.game.GamePriceVO;
import com.game.community.model.vo.game.GameListItemVO;
import com.game.community.steam.client.SteamChartClient;
import com.game.community.steam.mapper.GameCatalogMapper;
import com.game.community.steam.mapper.GameChartSnapshotMapper;
import com.game.community.steam.service.GameCatalogService;
import com.game.community.steam.service.GameChartService;
import com.game.community.utils.RedisUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameChartServiceImpl implements GameChartService {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private final SteamChartClient steamChartClient;
    private final GameCatalogService gameCatalogService;
    private final GameCatalogMapper gameCatalogMapper;
    private final GameChartSnapshotMapper gameChartSnapshotMapper;
    private final RedisUtils redisUtils;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    /** 查询当前榜单快照，优先使用 Redis 缓存。 */
    @Override
    public List<GameChartItemVO> listChart(GameChartQuery query) {
        String board = query == null ? null : query.getBoard();
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
        List<GameChartSnapshot> snapshots = gameChartSnapshotMapper.selectList(
                new LambdaQueryWrapper<GameChartSnapshot>()
                        .eq(GameChartSnapshot::getBoardType, resolvedBoard)
                        .eq(GameChartSnapshot::getIsCurrent, 1)
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

    /** 从 Steam 拉取榜单并保存本地快照。 */
    @Override
    public void syncChart(String board) {
        String resolvedBoard = resolveBoard(board);
        List<GameListItemVO> games = fetchChartGamesInBatches(resolvedBoard);
        if (games.isEmpty()) {
            log.warn("Steam 榜单为空: board={}", resolvedBoard);
            return;
        }

        // 只保存榜单返回的轻量数据，不在榜单同步中请求完整 appdetails。
        for (GameListItemVO game : games) {
            gameCatalogService.upsertBasicCatalog(game);
        }
        List<Long> appIds = games.stream().map(GameListItemVO::getAppId).toList();
        String periodKey = currentDailyPeriodKey();
        LocalDateTime now = LocalDateTime.now(SHANGHAI);
        String snapshotId = UUID.randomUUID().toString().replace("-", "");
        transactionTemplate.executeWithoutResult(status -> replaceCurrentSnapshot(
                resolvedBoard, periodKey, snapshotId, now, appIds));
        redisUtils.del(SteamRedisConstants.GAME_CHART_KEY_PREFIX + resolvedBoard);
        // 榜单已可用后，再异步补充英文名等基础字段；成功后由目录服务发送 ES 事件。
        gameCatalogService.warmupBasicInfoAsync(appIds);
        log.info("游戏榜单同步完成 board={} period={} count={}", resolvedBoard, periodKey, appIds.size());
    }

    /** 按配置同步全部榜单，单个榜单失败不影响其他榜单。 */
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

    /** 用户翻页时按需扩展榜单，避免首次同步抓取完整 Steam 榜单。 */
    @Override
    public ChartCapacityStatus ensureChartCapacity(String board, int requiredCount) {
        String resolvedBoard = resolveBoard(board);
        int targetCount = Math.max(1, requiredCount);
        List<GameChartSnapshot> current = loadCurrentSnapshots(resolvedBoard);
        if (current.size() >= targetCount) {
            return ChartCapacityStatus.READY;
        }

        String lockKey = SteamApiConstants.CHART_EXPAND_LOCK_PREFIX + resolvedBoard;
        String lockToken = UUID.randomUUID().toString();
        Boolean acquired = redisUtils.setIfAbsent(
                lockKey, lockToken, SteamApiConstants.CHART_EXPAND_LOCK_SECONDS);
        if (!Boolean.TRUE.equals(acquired)) {
            return ChartCapacityStatus.IN_PROGRESS;
        }
        try {
            current = loadCurrentSnapshots(resolvedBoard);
            if (current.isEmpty()) {
                syncChart(resolvedBoard);
                current = loadCurrentSnapshots(resolvedBoard);
            }
            while (current.size() < targetCount) {
                int start = current.size();
                List<SteamChartGamePayload> batch = steamChartClient.fetchChartGames(
                        resolvedBoard, start, SteamApiConstants.CHART_REQUEST_PAGE_SIZE);
                if (batch.isEmpty()) {
                    return ChartCapacityStatus.EXHAUSTED;
                }
                List<GameListItemVO> newGames = toDistinctNewGames(batch, current);
                if (newGames.isEmpty()) {
                    return ChartCapacityStatus.EXHAUSTED;
                }
                appendChartBatch(resolvedBoard, current, newGames);
                newGames.forEach(gameCatalogService::upsertBasicCatalog);
                gameCatalogService.warmupBasicInfoAsync(
                        newGames.stream().map(GameListItemVO::getAppId).toList());
                current = loadCurrentSnapshots(resolvedBoard);
                if (batch.size() < SteamApiConstants.CHART_REQUEST_PAGE_SIZE) {
                    return current.size() >= targetCount
                            ? ChartCapacityStatus.READY : ChartCapacityStatus.EXHAUSTED;
                }
            }
            return ChartCapacityStatus.READY;
        } finally {
            redisUtils.unlock(lockKey, lockToken);
        }
    }

    /** 按 Steam 分页结果抓取完整榜单，去重后保存基础数据。 */
    private List<GameListItemVO> fetchChartGamesInBatches(String board) {
        Map<Long, GameListItemVO> games = new LinkedHashMap<>();
        for (int start = 0; start < SteamApiConstants.CHART_LIMIT;
             start += SteamApiConstants.CHART_REQUEST_PAGE_SIZE) {
            List<SteamChartGamePayload> batch = steamChartClient.fetchChartGames(
                    board, start, SteamApiConstants.CHART_REQUEST_PAGE_SIZE);
            if (batch.isEmpty()) {
                break;
            }
            int previousSize = games.size();
            batch.stream()
                    .filter(game -> game != null && game.getAppId() != null && game.getAppId() > 0)
                    .map(this::toListItemVO)
                    .forEach(game -> games.putIfAbsent(game.getAppId(), game));
            if (games.size() == previousSize) {
                // Steam 某些榜单在末页可能重复返回上一页；此时视为分页结束，
                // 不应让本次榜单同步被误判为失败。
                break;
            }
            if (batch.size() < SteamApiConstants.CHART_REQUEST_PAGE_SIZE) {
                break;
            }
        }
        return new ArrayList<>(games.values());
    }

    /** 过滤已经进入当前快照的游戏，避免 Steam 分页重复时中断后续扩展。 */
    private List<GameListItemVO> toDistinctNewGames(
            List<SteamChartGamePayload> batch, List<GameChartSnapshot> current) {
        Map<Long, Boolean> existing = new LinkedHashMap<>();
        current.forEach(snapshot -> existing.put(snapshot.getAppId(), Boolean.TRUE));
        Map<Long, GameListItemVO> result = new LinkedHashMap<>();
        batch.stream()
                .filter(game -> game != null && game.getAppId() != null && game.getAppId() > 0)
                .map(this::toListItemVO)
                .filter(game -> !existing.containsKey(game.getAppId()))
                .forEach(game -> result.putIfAbsent(game.getAppId(), game));
        return new ArrayList<>(result.values());
    }

    /** 在当前快照末尾追加一批榜单记录，并清理列表缓存。 */
    private void appendChartBatch(
            String board, List<GameChartSnapshot> current, List<GameListItemVO> games) {
        if (games.isEmpty()) {
            return;
        }
        String periodKey = current.isEmpty()
                ? currentDailyPeriodKey() : current.get(0).getPeriodKey();
        LocalDateTime now = LocalDateTime.now(SHANGHAI);
        String snapshotId = current.isEmpty()
                ? UUID.randomUUID().toString().replace("-", "")
                : current.get(0).getSnapshotId();
        int[] nextRank = {current.size() + 1};
        transactionTemplate.executeWithoutResult(status -> {
            for (GameListItemVO game : games) {
                GameChartSnapshot row = new GameChartSnapshot();
                row.setBoardType(board);
                row.setPeriodKey(periodKey);
                row.setAppId(game.getAppId());
                row.setRankNo(nextRank[0]++);
                row.setSnapshotTime(now);
                row.setSnapshotId(snapshotId);
                row.setIsCurrent(1);
                gameChartSnapshotMapper.insert(row);
            }
        });
        redisUtils.del(SteamRedisConstants.GAME_CHART_KEY_PREFIX + board);
    }

    /** 查询指定榜单的当前快照。 */
    private List<GameChartSnapshot> loadCurrentSnapshots(String board) {
        return gameChartSnapshotMapper.selectList(
                new LambdaQueryWrapper<GameChartSnapshot>()
                        .eq(GameChartSnapshot::getBoardType, board)
                        .eq(GameChartSnapshot::getIsCurrent, 1)
                        .orderByAsc(GameChartSnapshot::getRankNo));
    }

    /** 将 Steam 榜单 payload 转成统一游戏列表项。 */
    private GameListItemVO toListItemVO(SteamChartGamePayload payload) {
        GameListItemVO target = new GameListItemVO();
        target.setAppId(payload.getAppId());
        target.setName(payload.getName());
        target.setCoverUrl(payload.getCoverUrl());
        target.setPrice(toPriceVO(payload.getPrice()));
        return target;
    }

    /** 将 Steam 价格 payload 转成对外价格 VO。 */
    private GamePriceVO toPriceVO(SteamPricePayload payload) {
        if (payload == null) {
            return null;
        }
        GamePriceVO target = new GamePriceVO();
        target.setFree(payload.getFree());
        target.setCurrency(payload.getCurrency());
        target.setInitial(payload.getInitial());
        target.setFinalPrice(payload.getFinalPrice());
        target.setDiscountPercent(payload.getDiscountPercent());
        target.setDiscountEndAt(payload.getDiscountEndAt());
        target.setFormatted(payload.getFormatted());
        return target;
    }

    /** 在事务内写入新快照并原子切换当前版本，避免用户读到半成品榜单。 */
    private void replaceCurrentSnapshot(
            String board,
            String periodKey,
            String snapshotId,
            LocalDateTime snapshotTime,
            List<Long> appIds) {
        gameChartSnapshotMapper.update(
                null,
                new LambdaUpdateWrapper<GameChartSnapshot>()
                        .eq(GameChartSnapshot::getBoardType, board)
                        .eq(GameChartSnapshot::getIsCurrent, 1)
                        .set(GameChartSnapshot::getIsCurrent, 0));
        gameChartSnapshotMapper.delete(new LambdaQueryWrapper<GameChartSnapshot>()
                .eq(GameChartSnapshot::getBoardType, board)
                .eq(GameChartSnapshot::getPeriodKey, periodKey));

        int rank = 1;
        for (int start = 0; start < appIds.size(); start += SteamApiConstants.CHART_BATCH_SIZE) {
            int end = Math.min(start + SteamApiConstants.CHART_BATCH_SIZE, appIds.size());
            for (Long appId : appIds.subList(start, end)) {
                GameChartSnapshot row = new GameChartSnapshot();
                row.setBoardType(board);
                row.setPeriodKey(periodKey);
                row.setAppId(appId);
                row.setRankNo(rank++);
                row.setSnapshotTime(snapshotTime);
                row.setSnapshotId(snapshotId);
                row.setIsCurrent(1);
                gameChartSnapshotMapper.insert(row);
            }
        }
    }

    /** 校验并规范化榜单类型，避免使用未配置的榜单查询或写入数据。 */
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

    /** 按榜单快照中的 App ID 批量读取本地游戏目录。 */
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

    /** 将本地游戏目录转换为榜单展示对象。 */
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
        vo.setSteamReviewCount(base.getSteamReviewCount());
        vo.setDeveloper(base.getDeveloper());
        vo.setPublisher(base.getPublisher());
        vo.setReleaseDate(base.getReleaseDate());
        vo.setPrice(base.getPrice());
        return vo;
    }

    /** 生成上海时区下的每日快照版本号。 */
    private String currentDailyPeriodKey() {
        return LocalDate.now(SHANGHAI).toString();
    }
}
