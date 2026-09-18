package com.game.community.search.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.DeleteByQueryRequest;
import co.elastic.clients.elasticsearch.core.DeleteRequest;
import co.elastic.clients.elasticsearch.core.GetResponse;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOrder;
import com.game.community.model.elasticsearch.ArticleDocument;
import com.game.community.model.elasticsearch.GameIndexDocument;
import com.game.community.model.elasticsearch.SuggestDocument;
import com.game.community.model.vo.game.GameListItemVO;
import com.game.community.common.constant.search.SearchConstants;
import com.game.community.search.service.ElasticsearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class ElasticsearchServiceImpl implements ElasticsearchService {

    private final ElasticsearchClient elasticsearchClient;
    private final DataSource dataSource;

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
    public void updateArticleEmbedding(Long articleId, List<Float> vector) {
        if (articleId == null || vector == null || vector.isEmpty()) {
            return;
        }
        try {
            elasticsearchClient.update(request -> request
                            .index(SearchConstants.ARTICLE_INDEX)
                            .id(String.valueOf(articleId))
                            .doc(Map.of("embedding", vector)),
                    ArticleDocument.class);
            log.info("文章 embedding 异步回写成功: articleId={}", articleId);
        } catch (IOException e) {
            throw new IllegalStateException("文章 embedding 回写失败", e);
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
    public void deleteArticlesNotIn(Set<Long> articleIds) {
        deleteDocumentsNotIn(SearchConstants.ARTICLE_INDEX, "id", articleIds, ArticleDocument.class,
                ArticleDocument::getId, "文章");
    }

    @Override
    public void indexGame(GameIndexDocument document) {
        if (document == null || document.getAppId() == null) {
            return;
        }
        indexGameInternal(document, document.getUpdatedAt());
    }

    @Override
    public void indexGame(GameListItemVO game) {
        indexGame(game, null);
    }

    @Override
    public void indexGame(GameListItemVO game, LocalDateTime eventTime) {
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
        document.setGenreText(game.getGenres() == null ? "" : String.join(" ", game.getGenres()));
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
        document.setUpdatedAt(eventTime);
        indexGameInternal(document, eventTime);
    }

    /**
     * 游戏候选回源和 Kafka 更新共享同一业务 ID 锁，防止不完整的候选数据覆盖更新更晚的完整事件。
     * eventTime 为空代表回源候选：已有文档时只补建议词，不覆盖现有索引。
     */
    private void indexGameInternal(GameIndexDocument document, LocalDateTime eventTime) {
        try (Connection connection = dataSource.getConnection()) {
            if (!tryAcquireGameLock(connection, document.getAppId())) {
                return;
            }
            try {
                GetResponse<GameIndexDocument> current = elasticsearchClient.get(request -> request
                                .index(SearchConstants.GAME_INDEX)
                                .id(String.valueOf(document.getAppId())), GameIndexDocument.class);
                if (current.found() && current.source() != null) {
                    LocalDateTime currentTime = current.source().getUpdatedAt();
                    if (eventTime == null || (currentTime != null && eventTime.isBefore(currentTime))) {
                        log.debug("跳过可能覆盖新版本的游戏索引写入: appId={}, eventTime={}, currentTime={}",
                                document.getAppId(), eventTime, currentTime);
                        return;
                    }
                }
                elasticsearchClient.index(IndexRequest.of(i -> i
                        .index(SearchConstants.GAME_INDEX)
                        .id(String.valueOf(document.getAppId()))
                        .document(document)));
            } finally {
                releaseGameLock(connection, document.getAppId());
            }
        } catch (IOException e) {
            throw new IllegalStateException("游戏索引同步失败", e);
        } catch (Exception e) {
            throw new IllegalStateException("游戏索引并发保护失败", e);
        }
    }

    private boolean tryAcquireGameLock(Connection connection, Long appId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("SELECT GET_LOCK(?, 0)")) {
            statement.setString(1, SearchConstants.GAME_INDEX_LOCK_PREFIX + appId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getInt(1) == 1;
            }
        }
    }

    private void releaseGameLock(Connection connection, Long appId) {
        try (PreparedStatement statement = connection.prepareStatement("SELECT RELEASE_LOCK(?)")) {
            statement.setString(1, SearchConstants.GAME_INDEX_LOCK_PREFIX + appId);
            statement.execute();
        } catch (Exception e) {
            log.warn("释放游戏索引锁失败: appId={}, reason={}", appId, e.getMessage());
        }
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
    public void deleteGamesNotIn(Set<Long> appIds) {
        deleteDocumentsNotIn(SearchConstants.GAME_INDEX, "appId", appIds, GameIndexDocument.class,
                GameIndexDocument::getAppId, "游戏");
    }

    /**
     * 全量重建完成后只删除源数据中已经不存在的 ES 文档，避免“只 upsert 不清理”导致陈旧结果长期残留。
     * 通过 search_after 枚举 ES 中的全部业务 ID，不依赖 index.max_result_window。
     */
    private <T> void deleteDocumentsNotIn(String index,
                                           String idField,
                                           Set<Long> retainedIds,
                                           Class<T> documentType,
                                           java.util.function.Function<T, Long> idExtractor,
                                           String resourceName) {
        Set<Long> indexedIds = collectIds(index, idField, documentType, idExtractor);
        List<Long> staleIds = indexedIds.stream()
                .filter(id -> retainedIds == null || !retainedIds.contains(id))
                .toList();
        if (staleIds.isEmpty()) {
            log.info("{}索引重建无需清理陈旧文档: index={}", resourceName, index);
            return;
        }
        try {
            for (int from = 0; from < staleIds.size(); from += 500) {
                List<Long> batch = staleIds.subList(from, Math.min(from + 500, staleIds.size()));
                BulkRequest.Builder bulk = new BulkRequest.Builder();
                batch.forEach(id -> bulk.operations(op -> op.delete(delete -> delete
                        .index(index)
                        .id(String.valueOf(id)))));
                BulkResponse response = elasticsearchClient.bulk(bulk.build());
                if (response.errors()) {
                    response.items().stream()
                            .filter(item -> item.error() != null)
                            .forEach(item -> log.warn("{}陈旧索引文档删除失败: id={}, reason={}",
                                    resourceName, item.id(), item.error().reason()));
                }
            }
            log.info("{}索引陈旧文档清理完成: index={}, deleted={}", resourceName, index, staleIds.size());
        } catch (IOException e) {
            throw new IllegalStateException(resourceName + "索引陈旧文档清理失败", e);
        }
    }

    private <T> Set<Long> collectIds(String index,
                                     String idField,
                                     Class<T> documentType,
                                     java.util.function.Function<T, Long> idExtractor) {
        Set<Long> ids = new HashSet<>();
        Long lastId = null;
        try {
            while (true) {
                SearchRequest.Builder request = new SearchRequest.Builder()
                        .index(index)
                        .size(500)
                        .source(source -> source.filter(filter -> filter.includes(idField)))
                        .query(query -> query.matchAll(matchAll -> matchAll))
                        .sort(sort -> sort.field(field -> field.field(idField).order(SortOrder.Asc)));
                if (lastId != null) {
                    request.searchAfter(FieldValue.of(lastId));
                }
                SearchResponse<T> response = elasticsearchClient.search(request.build(), documentType);
                List<T> documents = response.hits().hits().stream()
                        .map(hit -> hit.source())
                        .filter(java.util.Objects::nonNull)
                        .toList();
                if (documents.isEmpty()) {
                    return ids;
                }
                int previousSize = ids.size();
                documents.stream().map(idExtractor).filter(java.util.Objects::nonNull).forEach(ids::add);
                if (ids.size() == previousSize) {
                    throw new IllegalStateException("ES 全量索引 ID 枚举未前进: index=" + index);
                }
                lastId = documents.stream().map(idExtractor).filter(java.util.Objects::nonNull).max(Long::compareTo).orElse(null);
                if (lastId == null || documents.size() < 500) {
                    return ids;
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("枚举" + index + "索引文档失败", e);
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
    public void clearSuggestions() {
        try {
            elasticsearchClient.deleteByQuery(DeleteByQueryRequest.of(d -> d
                    .index(SearchConstants.SUGGEST_INDEX)
                    .query(q -> q.matchAll(m -> m))));
        } catch (IOException e) {
            throw new IllegalStateException("建议词索引全量清理失败", e);
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
