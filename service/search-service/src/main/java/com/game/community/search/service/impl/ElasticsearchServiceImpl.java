package com.game.community.search.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.DeleteRequest;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import com.game.community.model.elasticsearch.ArticleDocument;
import com.game.community.model.elasticsearch.GameIndexDocument;
import com.game.community.model.elasticsearch.SuggestDocument;
import com.game.community.model.vo.game.GameListItemVO;
import com.game.community.common.constant.search.SearchConstants;
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
                    .index(SearchConstants.ARTICLE_INDEX)
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
                    .index(SearchConstants.ARTICLE_INDEX)
                    .id(String.valueOf(articleId))
            ));
            log.info("文章索引删除成功: articleId={}", articleId);
        } catch (IOException e) {
            throw new IllegalStateException("文章索引删除失败", e);
        }
    }

    @Override
    public void indexGame(GameIndexDocument document) {
        if (document == null || document.getAppId() == null) {
            return;
        }
        try {
            elasticsearchClient.index(IndexRequest.of(i -> i
                    .index(SearchConstants.GAME_INDEX)
                    .id(String.valueOf(document.getAppId()))
                    .document(document)));
        } catch (IOException e) {
            throw new IllegalStateException("游戏索引同步失败", e);
        }
    }

    @Override
    public void indexGame(GameListItemVO game) {
        if (game == null || game.getAppId() == null) {
            return;
        }
        GameIndexDocument document = new GameIndexDocument();
        document.setAppId(game.getAppId());
        document.setName(game.getName());
        document.setNameZh(game.getNameZh());
        document.setNameEn(game.getNameEn());
        document.setAliases(game.getAliases() == null ? List.of() : game.getAliases());
        document.setDevelopers(game.getDeveloper() == null ? List.of() : List.of(game.getDeveloper()));
        document.setPublishers(game.getPublisher() == null ? List.of() : List.of(game.getPublisher()));
        document.setGenres(game.getGenres() == null ? List.of() : game.getGenres());
        document.setCoverUrl(game.getCoverUrl());
        document.setReleaseDate(game.getReleaseDate());
        document.setSteamReviewScore(game.getSteamReviewScore());
        document.setSteamReviewCount(game.getSteamReviewCount());
        document.setAvgScore(game.getAvgScore());
        document.setReviewCount(game.getReviewCount());
        document.setDiscussCount(game.getDiscussCount());
        document.setPrice(game.getPrice());
        document.setStatus(SearchConstants.GAME_STATUS_ACTIVE);
        document.setDetailReady(false);
        document.setUpdatedAt(java.time.LocalDateTime.now());
        indexGame(document);
    }

    @Override
    public void deleteGame(Long appId) {
        if (appId == null) {
            return;
        }
        try {
            elasticsearchClient.delete(DeleteRequest.of(d -> d
                    .index(SearchConstants.GAME_INDEX)
                    .id(String.valueOf(appId))));
        } catch (IOException e) {
            throw new IllegalStateException("游戏索引删除失败", e);
        }
    }

    @Override
    public void indexSuggestion(SuggestDocument document) {
        if (document == null || document.getId() == null) {
            return;
        }
        try {
            elasticsearchClient.index(IndexRequest.of(i -> i
                    .index(SearchConstants.SUGGEST_INDEX)
                    .id(String.valueOf(document.getId()))
                    .document(document)
            ));
        } catch (IOException e) {
            throw new IllegalStateException("建议词索引写入失败", e);
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
                        .index(SearchConstants.SUGGEST_INDEX)
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
                    .index(SearchConstants.SUGGEST_INDEX)
                    .id(String.valueOf(id))
            ));
        } catch (IOException e) {
            throw new IllegalStateException("建议词索引删除失败", e);
        }
    }
}
