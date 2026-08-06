package com.game.community.search.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.game.community.feign.SteamFeignClient;
import com.game.community.model.base.PageResult;
import com.game.community.model.base.Result;
import com.game.community.model.elasticsearch.GameIndexDocument;
import com.game.community.model.vo.game.GameListItemVO;
import com.game.community.model.vo.game.GameTagVO;
import com.game.community.common.constant.search.SearchConstants;
import com.game.community.search.service.ElasticsearchService;
import com.game.community.search.service.GameSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameSearchServiceImpl implements GameSearchService {

    private final ElasticsearchClient elasticsearchClient;
    private final ElasticsearchService elasticsearchService;
    private final SteamFeignClient steamFeignClient;
    private final GameIndexAsyncService gameIndexAsyncService;

    @Override
    public PageResult<GameListItemVO> search(String keyword, Long page, Long size) {
        String query = keyword == null ? "" : keyword.trim();
        long pageNo = page == null || page < 1 ? 1 : Math.min(page, SearchConstants.SEARCH_MAX_PAGE_NUMBER);
        long pageSize = size == null || size < 1 ? SearchConstants.GAME_SEARCH_DEFAULT_SIZE : Math.min(size, SearchConstants.SEARCH_PAGE_MAX_SIZE);
        if (!StringUtils.hasText(query)) {
            return PageResult.of(List.of(), pageNo, pageSize, 0L);
        }
        try {
            SearchResponse<GameIndexDocument> response = elasticsearchClient.search(s -> s
                            .index(SearchConstants.GAME_INDEX)
                            .from((int) ((pageNo - 1) * pageSize))
                            .size((int) pageSize)
                            .query(q -> q.bool(b -> b
                                    .must(buildGameKeywordQuery(query))
                                    .filter(f -> f.term(t -> t.field("status").value(1)))))
                            .sort(so -> so.score(sc -> sc.order(SortOrder.Desc)))
                            .sort(so -> so.field(f -> f.field("steamReviewCount").order(SortOrder.Desc))),
                    GameIndexDocument.class);
            List<GameListItemVO> items = response.hits().hits().stream()
                    .map(Hit::source)
                    .filter(Objects::nonNull)
                    .map(this::toListItem)
                    .toList();
            long total = response.hits().total() == null ? items.size()
                    : response.hits().total().value();
            if (!items.isEmpty()) {
                return PageResult.of(items, pageNo, pageSize, total);
            }
        } catch (Exception e) {
            log.warn("游戏 ES 搜索失败，回源 Steam: keyword={}", query, e);
        }

        List<GameListItemVO> candidates = searchSteam(query, 0, SearchConstants.SEARCH_PAGE_MAX_SIZE);
        List<GameListItemVO> matched = candidates.stream()
                .filter(game -> matchesGameKeyword(game, query))
                .toList();
        if (!matched.isEmpty()) {
            return pageFromMatches(matched, pageNo, pageSize);
        }

        // Steam Store 无结果时，用 Steam 服务维护的本地目录做最后兜底，随后异步补入 ES。
        PageResult<GameListItemVO> catalogResult = searchCatalog(query, pageNo, pageSize);
        if (catalogResult != null && catalogResult.getData() != null
                && !catalogResult.getData().isEmpty()) {
            gameIndexAsyncService.indexCandidates(catalogResult.getData());
            return catalogResult;
        }
        return PageResult.of(List.of(), pageNo, pageSize, 0L);
    }

    private PageResult<GameListItemVO> pageFromMatches(List<GameListItemVO> matches,
                                                       long page, long size) {
        int start = (int) Math.min((page - 1) * size, matches.size());
        int end = Math.min(start + (int) size, matches.size());
        gameIndexAsyncService.indexCandidates(matches);
        return PageResult.of(matches.subList(start, end), page, size, (long) matches.size());
    }

    private PageResult<GameListItemVO> searchCatalog(String keyword, long page, long size) {
        try {
            return steamFeignClient.searchCatalogGames(keyword, (int) page, (int) size);
        } catch (Exception e) {
            log.warn("Steam 本地游戏库搜索失败，继续回源 Store: keyword={}", keyword, e);
            return null;
        }
    }

    @Override
    public List<GameTagVO> listTags(List<Long> appIds) {
        List<Long> ids = appIds == null ? List.of() : appIds.stream()
                .filter(Objects::nonNull).distinct().limit(SearchConstants.GAME_TAG_MAX_IDS).toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        try {
            Map<Long, GameIndexDocument> documents = new LinkedHashMap<>();
            SearchResponse<GameIndexDocument> response = elasticsearchClient.search(s -> s
                            .index(SearchConstants.GAME_INDEX)
                            .size(ids.size())
                            .query(q -> q.terms(t -> t.field("appId")
                                    .terms(values -> values.value(ids.stream()
                                            .map(FieldValue::of).toList())))),
                    GameIndexDocument.class);
            response.hits().hits().forEach(item -> {
                if (item.source() != null && item.source().getAppId() != null) {
                    documents.put(item.source().getAppId(), item.source());
                }
            });
            List<GameTagVO> result = new ArrayList<>();
            List<Long> missingIds = new ArrayList<>();
            for (Long id : ids) {
                GameIndexDocument document = documents.get(id);
                GameTagVO tag = new GameTagVO();
                tag.setAppId(id);
                if (document != null) {
                    tag.setName(document.getName());
                    tag.setHeaderImage(document.getCoverUrl());
                } else {
                    missingIds.add(id);
                }
                result.add(tag);
            }
            mergeFallbackTags(result, missingIds);
            return result;
        } catch (Exception e) {
            log.warn("批量读取游戏标签索引失败", e);
            List<GameTagVO> result = ids.stream().map(id -> {
                GameTagVO tag = new GameTagVO();
                tag.setAppId(id);
                return tag;
            }).toList();
            mergeFallbackTags(result, ids);
            return result;
        }
    }

    private void mergeFallbackTags(List<GameTagVO> target, List<Long> missingIds) {
        if (missingIds.isEmpty()) {
            return;
        }
        try {
            Result<List<GameTagVO>> fallback = steamFeignClient.listGameTags(missingIds);
            if (fallback == null || fallback.getData() == null) {
                return;
            }
            Map<Long, GameTagVO> fallbackMap = fallback.getData().stream()
                    .filter(Objects::nonNull)
                    .collect(java.util.stream.Collectors.toMap(
                            GameTagVO::getAppId, item -> item, (left, right) -> left));
            target.forEach(tag -> {
                GameTagVO fallbackTag = fallbackMap.get(tag.getAppId());
                if (fallbackTag == null) {
                    return;
                }
                if (!StringUtils.hasText(tag.getName())) {
                    tag.setName(fallbackTag.getName());
                }
                if (!StringUtils.hasText(tag.getHeaderImage())) {
                    tag.setHeaderImage(fallbackTag.getHeaderImage());
                }
            });
        } catch (Exception e) {
            log.warn("游戏标签索引回源 Steam 失败", e);
        }
    }

    @Override
    public void index(GameListItemVO game) {
        elasticsearchService.indexGame(game);
    }

    @Override
    public void delete(Long appId) {
        elasticsearchService.deleteGame(appId);
    }

    @Override
    public void rebuildFromSteam() {
        int page = 1;
        while (true) {
            PageResult<GameListItemVO> result = steamFeignClient.listGameIndexPage(page, SearchConstants.GAME_REBUILD_PAGE_SIZE);
            if (result == null || result.getData() == null || result.getData().isEmpty()) {
                return;
            }
            result.getData().forEach(this::index);
            if ((long) page * SearchConstants.GAME_REBUILD_PAGE_SIZE >= (result.getTotal() == null ? 0 : result.getTotal())) {
                return;
            }
            page++;
        }
    }

    private List<GameListItemVO> searchSteam(String keyword, int start, int size) {
        try {
            Result<List<GameListItemVO>> result = steamFeignClient.searchGames(keyword, start, size);
            return result == null || result.getData() == null ? List.of() : result.getData();
        } catch (Exception e) {
            log.warn("Steam 轻量游戏搜索失败: keyword={}", keyword, e);
            return List.of();
        }
    }

    /**
     * 游戏搜索只匹配游戏名称，完整短语优先，名称分词 AND 作为兼容补充。
     * 这样不会因为别名、开发商、类型或简介中的单个词召回无关游戏。
     */
    private Query buildGameKeywordQuery(String keyword) {
        return Query.of(q -> q.bool(b -> b
                .should(s -> s.multiMatch(m -> m
                        .query(keyword)
                        .fields("name^10")
                        .type(TextQueryType.Phrase)))
                .should(s -> s.match(m -> m
                        .field("name")
                        .query(keyword)
                        .operator(Operator.And)
                        .boost(5.0F)))
                .minimumShouldMatch("1")));
    }

    private boolean matchesGameKeyword(GameListItemVO game, String keyword) {
        if (game == null || !StringUtils.hasText(game.getName())) {
            return false;
        }
        String normalizedName = normalizeSearchText(game.getName());
        String normalizedKeyword = normalizeSearchText(keyword);
        return StringUtils.hasText(normalizedKeyword) && normalizedName.contains(normalizedKeyword);
    }

    private String normalizeSearchText(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replaceAll("[\\s\\p{Punct}]+", "");
    }

    private GameListItemVO toListItem(GameIndexDocument document) {
        GameListItemVO item = new GameListItemVO();
        item.setAppId(document.getAppId());
        item.setName(document.getName());
        item.setCoverUrl(document.getCoverUrl());
        item.setGenres(document.getGenres());
        item.setDeveloper(first(document.getDevelopers()));
        item.setPublisher(first(document.getPublishers()));
        item.setReleaseDate(document.getReleaseDate());
        item.setSteamReviewScore(document.getSteamReviewScore());
        item.setSteamReviewCount(document.getSteamReviewCount());
        item.setAvgScore(document.getAvgScore());
        item.setReviewCount(document.getReviewCount());
        item.setDiscussCount(document.getDiscussCount());
        item.setPrice(document.getPrice());
        return item;
    }

    private String first(List<String> values) {
        return values == null || values.isEmpty() ? null : values.get(0);
    }
}
