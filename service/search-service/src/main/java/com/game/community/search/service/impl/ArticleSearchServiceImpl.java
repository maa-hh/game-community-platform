package com.game.community.search.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.game.community.common.constant.content.ContentConstants;
import com.game.community.common.constant.search.SearchConstants;
import com.game.community.model.dto.search.SearchPageDTO;
import com.game.community.model.dto.search.SearchResult;
import com.game.community.model.elasticsearch.ArticleDocument;
import com.game.community.model.vo.article.ArticleSearchItemVO;
import com.game.community.search.ai.ArticleHybridScoreMerger;
import com.game.community.search.ai.ArticleHybridScoreMerger.ArticleHybridHit;
import com.game.community.search.ai.DashScopeClient;
import com.game.community.search.config.SearchAiProperties;
import com.game.community.search.service.ArticleSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleSearchServiceImpl implements ArticleSearchService {

    private final ElasticsearchClient elasticsearchClient;
    private final DashScopeClient dashScopeClient;
    private final SearchAiProperties searchAiProperties;

    @Override
    public SearchResult search(SearchPageDTO searchDTO) {
        if (searchDTO == null) {
            searchDTO = new SearchPageDTO();
        }
        int page = Math.min(Math.max(searchDTO.getPage() == null ? 1 : searchDTO.getPage(), 1),
                SearchConstants.SEARCH_MAX_PAGE_NUMBER);
        int size = Math.min(Math.max(searchDTO.getSize() == null ? SearchConstants.SEARCH_PAGE_DEFAULT_SIZE : searchDTO.getSize(), 1),
                SearchConstants.SEARCH_PAGE_MAX_SIZE);
        String keyword = searchDTO.getKeyword() == null ? "" : searchDTO.getKeyword().trim();
        Long categoryId = searchDTO.getCategoryId();
        boolean latestSort = "latest".equalsIgnoreCase(searchDTO.getSort()) || !StringUtils.hasText(keyword);

        if (latestSort) {
            return searchBm25Only(page, size, keyword, categoryId, true);
        }
        String mode = resolveMode(searchDTO.getMode());
        if (SearchConstants.SEARCH_MODE_SEMANTIC.equals(mode) && shouldUseSemantic(keyword)) {
            SearchResult semanticResult = searchSemantic(page, size, keyword, categoryId);
            if (semanticResult.isSuccess()) {
                return semanticResult;
            }
            log.warn("语义检索失败，回退词法检索: {}", semanticResult.getErrorMsg());
        } else if (SearchConstants.SEARCH_MODE_HYBRID.equals(mode) && shouldUseHybrid(keyword)) {
            SearchResult hybridResult = searchHybrid(page, size, keyword, categoryId);
            if (hybridResult.isSuccess()) {
                return hybridResult;
            }
            log.warn("混合检索失败，回退词法检索: {}", hybridResult.getErrorMsg());
        }
        return searchBm25Only(page, size, keyword, categoryId, false);
    }

    private String resolveMode(String requestedMode) {
        if (SearchConstants.SEARCH_MODE_SEMANTIC.equalsIgnoreCase(requestedMode)) {
            return SearchConstants.SEARCH_MODE_SEMANTIC;
        }
        if (SearchConstants.SEARCH_MODE_LEXICAL.equalsIgnoreCase(requestedMode)) {
            return SearchConstants.SEARCH_MODE_LEXICAL;
        }
        if (SearchConstants.SEARCH_MODE_HYBRID.equalsIgnoreCase(requestedMode)
                || searchAiProperties.isHybridEnabled()) {
            return SearchConstants.SEARCH_MODE_HYBRID;
        }
        return SearchConstants.SEARCH_MODE_LEXICAL;
    }

    private boolean shouldUseSemantic(String keyword) {
        return searchAiProperties.isEnabled()
                && searchAiProperties.isSemanticEnabled()
                && dashScopeClient.isConfigured()
                && StringUtils.hasText(keyword);
    }

    private boolean shouldUseHybrid(String keyword) {
        return searchAiProperties.isEnabled()
                && searchAiProperties.isHybridEnabled()
                && searchAiProperties.isSemanticEnabled()
                && dashScopeClient.isConfigured()
                && StringUtils.hasText(keyword);
    }

    private SearchResult searchSemantic(int page, int size, String keyword, Long categoryId) {
        int fetchSize = Math.min(Math.max(page * size, searchAiProperties.getHybridCandidateK()),
                SearchConstants.HYBRID_MAX_FETCH);
        try {
            List<Float> vector = dashScopeClient.embedding(keyword);
            if (vector.isEmpty()) {
                return failedResult("embedding unavailable");
            }
            Query filterQuery = buildFilterQuery(categoryId);
            SearchResponse<ArticleDocument> response = elasticsearchClient.search(s -> s
                            .index(SearchConstants.ARTICLE_INDEX)
                            .size(fetchSize)
                            .knn(k -> k
                                    .field("embedding")
                                    .queryVector(vector)
                                    .k(fetchSize)
                                    .numCandidates(Math.max(fetchSize, searchAiProperties.getHybridNumCandidates()))
                                    .filter(filterQuery)),
                    ArticleDocument.class);
            return toSearchResult(response.hits().hits().stream()
                    .filter(hit -> hit.source() != null)
                    .map(this::convertToItem)
                    .toList(), response.hits().total(), page, size);
        } catch (Exception e) {
            log.error("文章语义搜索失败: keyword={}, categoryId={}", keyword, categoryId, e);
            return failedResult("语义搜索暂不可用");
        }
    }

    private SearchResult searchHybrid(int page, int size, String keyword, Long categoryId) {
        int fetchSize = Math.min(
                Math.max(page * size, searchAiProperties.getHybridCandidateK()),
                SearchConstants.HYBRID_MAX_FETCH);
        try {
            Query filterQuery = buildFilterQuery(categoryId);
            SearchResponse<ArticleDocument> bm25Response = elasticsearchClient.search(s -> s
                            .index(SearchConstants.ARTICLE_INDEX)
                            .size(fetchSize)
                            .query(q -> q.bool(b -> b
                                    .must(buildArticleKeywordQuery(keyword))
                                    .filter(filterQuery))),
                    ArticleDocument.class);

            List<Float> vector = dashScopeClient.embedding(keyword);
            if (vector.isEmpty()) {
                SearchResult fallback = new SearchResult();
                fallback.setErrorMsg("embedding unavailable");
                return fallback;
            }

            SearchResponse<ArticleDocument> semanticResponse = elasticsearchClient.search(s -> s
                            .index(SearchConstants.ARTICLE_INDEX)
                            .size(fetchSize)
                            .knn(k -> k
                                    .field("embedding")
                                    .queryVector(vector)
                                    .k(fetchSize)
                                    .numCandidates(Math.max(fetchSize, searchAiProperties.getHybridNumCandidates()))
                                    .filter(filterQuery)),
                    ArticleDocument.class);

            List<ArticleHybridHit> bm25Hits = bm25Response.hits().hits().stream()
                    .filter(hit -> hit.source() != null)
                    .map(hit -> ArticleHybridScoreMerger.toHit(
                            hit.source(),
                            hit.score() == null ? 0F : hit.score().floatValue()))
                    .toList();
            List<ArticleHybridHit> semanticHits = semanticResponse.hits().hits().stream()
                    .filter(hit -> hit.source() != null)
                    .map(hit -> ArticleHybridScoreMerger.toHit(
                            hit.source(),
                            hit.score() == null ? 0F : hit.score().floatValue()))
                    .toList();

            // 混合检索保留两路召回的并集；否则它退化成“词法结果的语义重排”，无法召回同义表达。

            List<ArticleHybridHit> merged = ArticleHybridScoreMerger.merge(bm25Hits, semanticHits, fetchSize);
            int from = (page - 1) * size;
            List<ArticleSearchItemVO> items = new ArrayList<>();
            for (int i = from; i < Math.min(from + size, merged.size()); i++) {
                items.add(convertDocument(merged.get(i).getDocument()));
            }

            long bm25Total = Math.max(totalHits(bm25Response), totalHits(semanticResponse));
            SearchResult result = new SearchResult();
            result.setTotal(bm25Total);
            result.setPage((long) page);
            result.setSize((long) size);
            result.setList(items);
            return result;
        } catch (Exception e) {
            log.error("文章混合搜索失败: keyword={}, categoryId={}", keyword, categoryId, e);
            SearchResult result = new SearchResult();
            result.setErrorMsg("混合搜索暂不可用");
            return result;
        }
    }

    private SearchResult searchBm25Only(int page, int size, String keyword, Long categoryId, boolean latestSort) {
        try {
            BoolQuery.Builder boolQuery = new BoolQuery.Builder();
            if (StringUtils.hasText(keyword)) {
                boolQuery.must(buildArticleKeywordQuery(keyword));
            } else {
                boolQuery.must(Query.of(q -> q.matchAll(m -> m)));
            }
            boolQuery.filter(buildFilterQuery(categoryId));

            SearchRequest.Builder request = new SearchRequest.Builder()
                    .index(SearchConstants.ARTICLE_INDEX)
                    .query(Query.of(q -> q.bool(boolQuery.build())))
                    .from((page - 1) * size)
                    .size(size);
            if (latestSort) {
                request.sort(s -> s.field(f -> f.field("publishedTime").order(SortOrder.Desc)));
                request.sort(s -> s.field(f -> f.field("id").order(SortOrder.Desc)));
            } else {
                request.sort(s -> s.score(sc -> sc.order(SortOrder.Desc)));
                request.sort(s -> s.field(f -> f.field("publishedTime").order(SortOrder.Desc)));
                request.sort(s -> s.field(f -> f.field("id").order(SortOrder.Desc)));
            }

            SearchResponse<ArticleDocument> response = elasticsearchClient.search(request.build(), ArticleDocument.class);
            List<ArticleSearchItemVO> items = response.hits().hits().stream().map(this::convertToItem).toList();

            SearchResult result = new SearchResult();
            result.setTotal(response.hits().total() == null ? 0L : response.hits().total().value());
            result.setPage((long) page);
            result.setSize((long) size);
            result.setList(items);
            return result;
        } catch (Exception e) {
            log.error("文章搜索失败: keyword={}, categoryId={}", keyword, categoryId, e);
            SearchResult result = new SearchResult();
            result.setErrorMsg("搜索服务暂不可用，请稍后重试");
            return result;
        }
    }

    private SearchResult toSearchResult(List<ArticleSearchItemVO> items,
                                         co.elastic.clients.elasticsearch.core.search.TotalHits totalHits,
                                         int page,
                                         int size) {
        SearchResult result = new SearchResult();
        result.setTotal(totalHits == null ? (long) items.size() : totalHits.value());
        result.setPage((long) page);
        result.setSize((long) size);
        int from = Math.min((page - 1) * size, items.size());
        int to = Math.min(from + size, items.size());
        result.setList(items.subList(from, to));
        return result;
    }

    private long totalHits(SearchResponse<ArticleDocument> response) {
        return response.hits().total() == null ? response.hits().hits().size() : response.hits().total().value();
    }

    private SearchResult failedResult(String message) {
        SearchResult result = new SearchResult();
        result.setErrorMsg(message);
        return result;
    }

    private Query buildFilterQuery(Long categoryId) {
        BoolQuery.Builder filter = new BoolQuery.Builder()
                .filter(Query.of(q -> q.term(t -> t.field("status").value(ContentConstants.ArticleStatus.PUBLISHED))));
        if (categoryId != null) {
            filter.filter(Query.of(q -> q.bool(b -> b
                    .should(s -> s.term(t -> t.field("categoryId").value(categoryId)))
                    .should(s -> s.term(t -> t.field("categoryIds").value(categoryId)))
                    .minimumShouldMatch("1")
            )));
        }
        return Query.of(q -> q.bool(filter.build()));
    }

    /**
     * 标题和摘要按完整短语匹配，避免查询词被拆开后只命中其中一部分。
     * 各字段独立加权，至少命中一个有效字段才进入结果集。
     */
    private Query buildArticleKeywordQuery(String keyword) {
        return Query.of(q -> q.bool(b -> b
                .should(s -> s.multiMatch(m -> m
                        .query(keyword)
                        .fields("title^10")
                        .type(TextQueryType.Phrase)
                        .boost(2.0F)))
                .should(s -> s.multiMatch(m -> m
                        .query(keyword)
                        .fields("summary^7")
                        .type(TextQueryType.Phrase)
                        .boost(2.0F)))
                .should(s -> s.multiMatch(m -> m
                        .query(keyword)
                        .fields("content^3")
                        .type(TextQueryType.Phrase)
                        .boost(2.0F)))
                .should(s -> s.multiMatch(m -> m
                        .query(keyword)
                        .fields("gameTags.name^5")
                        .type(TextQueryType.Phrase)
                        .boost(2.0F)))
                .should(s -> s.multiMatch(m -> m
                        .query(keyword)
                        .fields("categoryNames^1", "categoryName^1")
                        .type(TextQueryType.Phrase)
                        .boost(2.0F)))
                .should(s -> s.multiMatch(m -> m
                        .query(keyword)
                        .fields("title^10", "summary^7", "gameTags.name^5", "content^3",
                                "categoryNames^1", "categoryName^1")
                        .operator(Operator.And)))
                .minimumShouldMatch("1")));
    }

    private ArticleSearchItemVO convertToItem(Hit<ArticleDocument> hit) {
        return convertDocument(hit.source());
    }

    private ArticleSearchItemVO convertDocument(ArticleDocument doc) {
        ArticleSearchItemVO item = new ArticleSearchItemVO();
        if (doc == null) {
            return item;
        }
        item.setId(doc.getId());
        item.setPublicId(doc.getPublicId());
        item.setAuthorAccountId(doc.getAuthorAccountId());
        item.setUsername(doc.getUsername());
        item.setAvatar(doc.getAvatar());
        item.setTitle(doc.getTitle());
        item.setSummary(doc.getSummary());
        item.setCoverUrl(doc.getCoverUrl());
        item.setPostType(doc.getPostType());
        item.setRefArticleId(doc.getRefArticleId());
        item.setRefArticle(doc.getRefArticle());
        item.setVideoUrl(doc.getVideoUrl());
        item.setCategoryId(doc.getCategoryId());
        item.setCategoryName(doc.getCategoryName());
        item.setCategoryIds(doc.getCategoryIds());
        item.setCategoryNames(doc.getCategoryNames());
        item.setGameTags(doc.getGameTags());
        item.setPublishedTime(doc.getPublishedTime());
        item.setCreateTime(doc.getCreateTime());
        item.setUpdateTime(doc.getUpdateTime());
        return item;
    }
}
