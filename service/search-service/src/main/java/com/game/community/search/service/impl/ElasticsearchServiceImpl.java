package com.game.community.search.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.DeleteRequest;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import com.game.community.model.elasticsearch.ArticleDocument;
import com.game.community.model.elasticsearch.SuggestDocument;
import com.game.community.search.initIndex.InitElasticsearchIndex;
import com.game.community.search.service.ElasticsearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ElasticsearchServiceImpl implements ElasticsearchService {

    private final ElasticsearchClient elasticsearchClient;

    @Override
    public void indexArticle(ArticleDocument document) {
        if (document == null || document.getId() == null) {
            return;
        }
        try {
            elasticsearchClient.index(IndexRequest.of(i -> i
                    .index(InitElasticsearchIndex.ARTICLE_INDEX)
                    .id(String.valueOf(document.getId()))
                    .document(document)
            ));
            log.info("文章索引同步成功: articleId={}", document.getId());
        } catch (IOException e) {
            throw new IllegalStateException("文章索引同步失败", e);
        }
    }

    @Override
    public void deleteArticle(Long articleId) {
        if (articleId == null) {
            return;
        }
        try {
            elasticsearchClient.delete(DeleteRequest.of(d -> d
                    .index(InitElasticsearchIndex.ARTICLE_INDEX)
                    .id(String.valueOf(articleId))
            ));
            log.info("文章索引删除成功: articleId={}", articleId);
        } catch (IOException e) {
            throw new IllegalStateException("文章索引删除失败", e);
        }
    }

    @Override
    public void batchAddSuggestions(List<SuggestDocument> documents) {
        if (documents == null || documents.isEmpty()) {
            return;
        }
        try {
            BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();
            for (SuggestDocument document : documents) {
                bulkBuilder.operations(op -> op.index(idx -> idx
                        .index(InitElasticsearchIndex.SUGGEST_INDEX)
                        .id(document.getId() == null ? null : String.valueOf(document.getId()))
                        .document(document)
                ));
            }
            BulkResponse response = elasticsearchClient.bulk(bulkBuilder.build());
            if (response.errors()) {
                response.items().stream()
                        .filter(item -> item.error() != null)
                        .forEach(item -> log.warn("建议词索引写入失败: id={}, reason={}", item.id(), item.error().reason()));
            }
        } catch (IOException e) {
            throw new IllegalStateException("建议词索引写入失败", e);
        }
    }

    @Override
    public void deleteSuggestion(Long id) {
        if (id == null) {
            return;
        }
        try {
            elasticsearchClient.delete(DeleteRequest.of(d -> d
                    .index(InitElasticsearchIndex.SUGGEST_INDEX)
                    .id(String.valueOf(id))
            ));
        } catch (IOException e) {
            throw new IllegalStateException("建议词索引删除失败", e);
        }
    }
}
