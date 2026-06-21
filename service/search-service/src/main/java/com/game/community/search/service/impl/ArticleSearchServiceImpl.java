package com.game.community.search.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.game.community.common.constant.content.ContentConstants;
import com.game.community.model.dto.search.SearchPageDTO;
import com.game.community.model.dto.search.SearchResult;
import com.game.community.model.elasticsearch.ArticleDocument;
import com.game.community.model.vo.article.ArticleSearchItemVO;
import com.game.community.search.initIndex.InitElasticsearchIndex;
import com.game.community.search.service.ArticleSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleSearchServiceImpl implements ArticleSearchService {

    private final ElasticsearchClient elasticsearchClient;

    @Override
    public SearchResult search(SearchPageDTO searchDTO) {
        int page = Math.max(searchDTO.getPage() == null ? 1 : searchDTO.getPage(), 1);
        int size = Math.min(Math.max(searchDTO.getSize() == null ? 10 : searchDTO.getSize(), 1), 50);
        String keyword = searchDTO.getKeyword() == null ? "" : searchDTO.getKeyword().trim();
        Long categoryId = searchDTO.getCategoryId();

        try {
            BoolQuery.Builder boolQuery = new BoolQuery.Builder();
            if (StringUtils.hasText(keyword)) {
                boolQuery.must(Query.of(q -> q.multiMatch(m -> m
                        .query(keyword)
                        .fields("title^4", "summary^2", "content", "categoryName")
                )));
            } else {
                boolQuery.must(Query.of(q -> q.matchAll(m -> m)));
            }
            boolQuery.filter(Query.of(q -> q.term(t -> t.field("status").value(ContentConstants.ArticleStatus.PUBLISHED))));
            if (categoryId != null) {
                boolQuery.filter(Query.of(q -> q.term(t -> t.field("categoryId").value(categoryId))));
            }

            SearchRequest.Builder request = new SearchRequest.Builder()
                    .index(InitElasticsearchIndex.ARTICLE_INDEX)
                    .query(Query.of(q -> q.bool(boolQuery.build())))
                    .from((page - 1) * size)
                    .size(size);
            if ("latest".equalsIgnoreCase(searchDTO.getSort()) || !StringUtils.hasText(keyword)) {
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
        } catch (IOException e) {
            log.error("文章搜索失败: keyword={}, categoryId={}", keyword, categoryId, e);
            SearchResult result = new SearchResult();
            result.setErrorMsg("搜索服务暂不可用，请稍后重试");
            return result;
        }
    }

    private ArticleSearchItemVO convertToItem(Hit<ArticleDocument> hit) {
        ArticleDocument doc = hit.source();
        ArticleSearchItemVO item = new ArticleSearchItemVO();
        if (doc == null) {
            return item;
        }
        item.setId(doc.getId());
        item.setUserId(doc.getUserId());
        item.setUsername(doc.getUsername());
        item.setAvatar(doc.getAvatar());
        item.setTitle(doc.getTitle());
        item.setSummary(doc.getSummary());
        item.setCoverUrl(doc.getCoverUrl());
        item.setCategoryId(doc.getCategoryId());
        item.setCategoryName(doc.getCategoryName());
        item.setPublishedTime(doc.getPublishedTime());
        item.setCreateTime(doc.getCreateTime());
        item.setUpdateTime(doc.getUpdateTime());
        return item;
    }
}
