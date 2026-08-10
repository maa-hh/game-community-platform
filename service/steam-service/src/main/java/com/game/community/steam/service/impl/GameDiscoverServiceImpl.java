package com.game.community.steam.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.game.community.common.constant.steam.GameBoardConstants;
import com.game.community.common.exception.BusinessException;
import com.game.community.steam.mapper.GameCatalogMapper;
import com.game.community.steam.mapper.GameChartSnapshotMapper;
import com.game.community.steam.service.GameCatalogService;
import com.game.community.steam.service.GameDiscoverService;
import com.game.community.model.base.PageResult;
import com.game.community.model.dto.game.GameDiscoverQuery;
import com.game.community.model.entity.game.GameCatalog;
import com.game.community.model.entity.game.GameChartSnapshot;
import com.game.community.model.vo.game.GameChartItemVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class GameDiscoverServiceImpl implements GameDiscoverService {

    private final GameCatalogMapper gameCatalogMapper;
    private final GameCatalogService gameCatalogService;
    private final GameChartSnapshotMapper gameChartSnapshotMapper;

    /** 规范化发现查询后，根据 all 或具体榜单选择分页策略。 */
    @Override
    public PageResult<GameChartItemVO> pageDiscover(GameDiscoverQuery query) {
        GameDiscoverQuery resolved = normalizeQuery(query);
        if (GameBoardConstants.ALL.equals(resolved.getBoard())) {
            return pageAllCatalog(resolved);
        }
        return pageChartBoard(resolved);
    }

    /** 从本地游戏目录分页查询全部游戏。 */
    private PageResult<GameChartItemVO> pageAllCatalog(GameDiscoverQuery query) {
        LambdaQueryWrapper<GameCatalog> wrapper = baseWrapper();
        applyFilters(wrapper, query);
        applySort(wrapper, query, null);
        Page<GameCatalog> page = gameCatalogMapper.selectPage(
                new Page<>(query.getPage(), query.getSize()), wrapper);
        List<GameChartItemVO> records = page.getRecords().stream()
                .map(this::toChartItem)
                .toList();
        gameCatalogService.refreshMetricsPriceAsync(
                records.stream().map(GameChartItemVO::getAppId).toList());
        return PageResult.of(records, page.getCurrent(), page.getSize(), page.getTotal());
    }

    /** 从定时任务生成的当前榜单快照分页查询，不触发 Steam API。 */
    private PageResult<GameChartItemVO> pageChartBoard(GameDiscoverQuery query) {
        List<GameChartSnapshot> snapshots = gameChartSnapshotMapper.selectList(
                new LambdaQueryWrapper<GameChartSnapshot>()
                        .eq(GameChartSnapshot::getBoardType, query.getBoard())
                        .eq(GameChartSnapshot::getIsCurrent, 1)
                        .orderByAsc(GameChartSnapshot::getRankNo));
        if (snapshots.isEmpty()) {
            return chartPageResult(List.of(), query, 0L);
        }

        Map<Long, Integer> rankMap = new HashMap<>();
        List<Long> appIds = new ArrayList<>();
        for (GameChartSnapshot snapshot : snapshots) {
            rankMap.put(snapshot.getAppId(), snapshot.getRankNo());
            appIds.add(snapshot.getAppId());
        }

        LambdaQueryWrapper<GameCatalog> wrapper = baseWrapper().in(GameCatalog::getAppId, appIds);
        applyFilters(wrapper, query);
        List<GameCatalog> catalogs = gameCatalogMapper.selectList(wrapper);
        List<GameCatalog> sorted = sortCatalogs(catalogs, query, rankMap);

        long total = sorted.size();
        long from = (query.getPage() - 1) * query.getSize();
        if (from >= total) {
            return chartPageResult(List.of(), query, total);
        }
        int to = (int) Math.min(from + query.getSize(), sorted.size());
        List<GameChartItemVO> records = sorted.subList((int) from, to).stream()
                .map(catalog -> {
                    GameChartItemVO vo = toChartItem(catalog);
                    vo.setRank(rankMap.get(catalog.getAppId()));
                    return vo;
                })
                .toList();
        gameCatalogService.refreshMetricsPriceAsync(
                records.stream().map(GameChartItemVO::getAppId).toList());
        return chartPageResult(records, query, total);
    }

    private PageResult<GameChartItemVO> chartPageResult(
            List<GameChartItemVO> records,
            GameDiscoverQuery query,
            long total) {
        PageResult<GameChartItemVO> result = PageResult.of(
                records, query.getPage(), query.getSize(), total);
        return result;
    }

    /** 统一发现参数默认值、合法榜单和排序字段。 */
    private GameDiscoverQuery normalizeQuery(GameDiscoverQuery query) {
        GameDiscoverQuery normalized = query == null ? new GameDiscoverQuery() : query;
        String board = normalized.getBoard() == null
                ? "all"
                : normalized.getBoard().trim().toLowerCase(Locale.ROOT);
        if (!GameBoardConstants.DISCOVER_BOARDS.contains(board)) {
            throw new BusinessException("榜单类型无效");
        }
        normalized.setBoard(board);
        normalized.setPage(normalized.getPage() == null || normalized.getPage() < 1 ? 1L : normalized.getPage());
        normalized.setSize(normalized.getSize() == null || normalized.getSize() < 1
                ? 20L
                : Math.min(normalized.getSize(), 50));
        String order = normalized.getOrder() == null
                ? "desc"
                : normalized.getOrder().trim().toLowerCase(Locale.ROOT);
        normalized.setOrder("asc".equals(order) ? "asc" : "desc");
        String sort = normalized.getSort();
        if (!StringUtils.hasText(sort)) {
            normalized.setSort(GameBoardConstants.ALL.equals(board) ? "steam_reviews" : "rank");
        } else {
            sort = sort.trim().toLowerCase(Locale.ROOT);
            if (!GameBoardConstants.SORT_FIELDS.contains(sort)) {
                throw new BusinessException("排序字段无效");
            }
            normalized.setSort(sort);
        }
        if (GameBoardConstants.ALL.equals(board) && "rank".equals(normalized.getSort())) {
            normalized.setSort("steam_score");
        }
        return normalized;
    }

    private LambdaQueryWrapper<GameCatalog> baseWrapper() {
        return new LambdaQueryWrapper<GameCatalog>().eq(GameCatalog::getStatus, 1);
    }

    private void applyFilters(LambdaQueryWrapper<GameCatalog> wrapper, GameDiscoverQuery query) {
        if (query.getMinSteamScore() != null) {
            wrapper.ge(GameCatalog::getSteamReviewScore, query.getMinSteamScore());
        }
        if (query.getMaxSteamScore() != null) {
            wrapper.le(GameCatalog::getSteamReviewScore, query.getMaxSteamScore());
        }
        if (query.getMinSteamReviews() != null) {
            wrapper.ge(GameCatalog::getSteamReviewCount, query.getMinSteamReviews());
        }
        if (query.getMaxSteamReviews() != null) {
            wrapper.le(GameCatalog::getSteamReviewCount, query.getMaxSteamReviews());
        }
        if (query.getMinPrice() != null) {
            wrapper.ge(GameCatalog::getPriceInitial, query.getMinPrice());
        }
        if (query.getMaxPrice() != null) {
            wrapper.le(GameCatalog::getPriceInitial, query.getMaxPrice());
        }
        if (query.getMinFinalPrice() != null) {
            wrapper.ge(GameCatalog::getPriceFinal, query.getMinFinalPrice());
        }
        if (query.getMaxFinalPrice() != null) {
            wrapper.le(GameCatalog::getPriceFinal, query.getMaxFinalPrice());
        }
        if (query.getMinDiscount() != null) {
            wrapper.ge(GameCatalog::getPriceDiscount, query.getMinDiscount());
        }
        if (Boolean.TRUE.equals(query.getDiscountOnly())) {
            wrapper.gt(GameCatalog::getPriceDiscount, 0);
        }
        if (Boolean.TRUE.equals(query.getFreeOnly())) {
            wrapper.eq(GameCatalog::getSteamIsFree, true);
        }
    }

    private void applySort(
            LambdaQueryWrapper<GameCatalog> wrapper,
            GameDiscoverQuery query,
            Map<Long, Integer> rankMap) {
        boolean asc = "asc".equals(query.getOrder());
        switch (query.getSort()) {
            case "steam_score" -> orderNullableColumn(wrapper, asc, "steam_review_score");
            case "steam_reviews" -> orderNullableColumn(wrapper, asc, "steam_review_count");
            case "price" -> orderNullableColumn(wrapper, asc, "price_initial");
            case "discount_price" -> orderNullableColumn(wrapper, asc, "price_final");
            case "rank" -> {
                if (rankMap != null) {
                    return;
                }
                wrapper.orderByDesc(GameCatalog::getUpdateTime)
                        .orderByDesc(GameCatalog::getAppId);
            }
            default -> wrapper.orderByDesc(GameCatalog::getUpdateTime)
                    .orderByDesc(GameCatalog::getAppId);
        }
    }

    /** MySQL 排序时把 NULL 放到最后，避免「暂无评价」的游戏排在最前。 */
    private void orderNullableColumn(
            LambdaQueryWrapper<GameCatalog> wrapper, boolean asc, String column) {
        String direction = asc ? "ASC" : "DESC";
        wrapper.last(
                "ORDER BY (" + column + " IS NULL), " + column + " " + direction + ", app_id DESC");
    }

    private List<GameCatalog> sortCatalogs(
            List<GameCatalog> catalogs,
            GameDiscoverQuery query,
            Map<Long, Integer> rankMap) {
        Comparator<GameCatalog> comparator = buildComparator(query.getSort(), query.getOrder(), rankMap);
        return catalogs.stream().sorted(comparator).toList();
    }

    private Comparator<GameCatalog> buildComparator(
            String sort, String order, Map<Long, Integer> rankMap) {
        boolean asc = "asc".equals(order);
        return switch (sort) {
            case "steam_score" -> intComparator(asc, GameCatalog::getSteamReviewScore);
            case "steam_reviews" -> intComparator(asc, GameCatalog::getSteamReviewCount);
            case "price" -> intComparator(asc, GameCatalog::getPriceInitial);
            case "discount_price" -> intComparator(asc, GameCatalog::getPriceFinal);
            default -> {
                Comparator<GameCatalog> rankComparator = Comparator.comparing(
                        catalog -> rankMap.getOrDefault(catalog.getAppId(), Integer.MAX_VALUE));
                yield asc ? rankComparator : rankComparator.reversed();
            }
        };
    }

    private Comparator<GameCatalog> intComparator(
            boolean asc, java.util.function.Function<GameCatalog, Integer> getter) {
        Comparator<Integer> valueOrder =
                asc ? Comparator.nullsLast(Integer::compareTo) : Comparator.nullsLast(Comparator.reverseOrder());
        return Comparator.comparing(getter, valueOrder);
    }

    private GameChartItemVO toChartItem(GameCatalog catalog) {
        GameChartItemVO vo = new GameChartItemVO();
        var base = gameCatalogService.toListItem(catalog);
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
}
