package com.game.community.search.initIndex;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import com.game.community.common.constant.search.SearchConstants;
import com.game.community.search.config.SearchIndexProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.function.Function;

@Slf4j
@Service
@RequiredArgsConstructor
public class InitElasticsearchIndex {

    private final ElasticsearchClient elasticsearchClient;
    private final SearchIndexProperties indexProperties;

    public void initIndex() {
        validateIndexProperties();
        createIndexIfAbsent(SearchConstants.ARTICLE_INDEX, this::articleIndexRequest, indexProperties.getArticleReplicas());
        ensureArticleEmbeddingMapping();
        createIndexIfAbsent(SearchConstants.SUGGEST_INDEX, this::suggestIndexRequest, indexProperties.getSuggestReplicas());
        createIndexIfAbsent(SearchConstants.GAME_INDEX, this::gameIndexRequest, indexProperties.getGameReplicas());
    }

    private void validateIndexProperties() {
        if (indexProperties.getArticleShards() < 1 || indexProperties.getSuggestShards() < 1
                || indexProperties.getGameShards() < 1
                || indexProperties.getArticleReplicas() < 0 || indexProperties.getSuggestReplicas() < 0
                || indexProperties.getGameReplicas() < 0) {
            throw new IllegalArgumentException("ES 分片数必须大于0，副本数不能小于0");
        }
    }

    private void createIndexIfAbsent(String indexName,
                                     Function<CreateIndexRequest.Builder, CreateIndexRequest> requestBuilder,
                                     int replicas) {
        try {
            if (indexExists(indexName)) {
                ensureReplicaCount(indexName, replicas);
                log.info("ES索引已存在: {}", indexName);
                return;
            }
            elasticsearchClient.indices().create(requestBuilder.apply(new CreateIndexRequest.Builder().index(indexName)));
            log.info("ES索引创建成功: {}", indexName);
        } catch (ElasticsearchException e) {
            if (isResourceAlreadyExists(e)) {
                log.info("ES索引已存在(并发创建): {}", indexName);
                return;
            }
            throw new IllegalStateException("创建ES索引失败: " + indexName, e);
        } catch (IOException e) {
            throw new IllegalStateException("创建ES索引失败: " + indexName, e);
        }
    }

    private boolean indexExists(String indexName) throws IOException {
        return elasticsearchClient.indices()
                .exists(ExistsRequest.of(e -> e.index(indexName)))
                .value();
    }

    private void ensureReplicaCount(String indexName, int replicas) throws IOException {
        if (replicas < 0) {
            throw new IllegalArgumentException("ES副本数不能小于0: " + replicas);
        }
        elasticsearchClient.indices().putSettings(s -> s
                .index(indexName)
                .settings(settings -> settings.numberOfReplicas(String.valueOf(replicas))));
    }

    private boolean isResourceAlreadyExists(ElasticsearchException exception) {
        if (exception.response() == null || exception.response().error() == null) {
            return false;
        }
        return "resource_already_exists_exception".equals(exception.response().error().type());
    }

    private CreateIndexRequest articleIndexRequest(CreateIndexRequest.Builder builder) {
        return builder
                .settings(s -> s
                        .numberOfShards(String.valueOf(indexProperties.getArticleShards()))
                        .numberOfReplicas(String.valueOf(indexProperties.getArticleReplicas()))
                )
                .mappings(m -> m
                        .properties("id", p -> p.long_(l -> l))
                        .properties("userId", p -> p.long_(l -> l))
                        .properties("username", p -> p.keyword(k -> k))
                        .properties("avatar", p -> p.keyword(k -> k))
                        .properties("title", p -> p.text(t -> t.analyzer("ik_max_word").searchAnalyzer("ik_smart")))
                        .properties("summary", p -> p.text(t -> t.analyzer("ik_max_word").searchAnalyzer("ik_smart")))
                        .properties("content", p -> p.text(t -> t.analyzer("ik_max_word").searchAnalyzer("ik_smart")))
                        .properties("coverUrl", p -> p.keyword(k -> k))
                        .properties("postType", p -> p.integer(i -> i))
                        .properties("refArticleId", p -> p.keyword(k -> k))
                        .properties("refArticle", p -> p.object(o -> o
                                .properties("id", r -> r.keyword(k -> k))
                                .properties("title", r -> r.text(t -> t.analyzer("ik_max_word").searchAnalyzer("ik_smart")))
                                .properties("summary", r -> r.text(t -> t.analyzer("ik_max_word").searchAnalyzer("ik_smart")))
                                .properties("coverUrl", r -> r.keyword(k -> k))
                                .properties("videoUrl", r -> r.keyword(k -> k))
                                .properties("postType", r -> r.integer(i -> i))
                                .properties("authorAccountId", r -> r.long_(l -> l))
                                .properties("username", r -> r.keyword(k -> k))
                                .properties("avatar", r -> r.keyword(k -> k))))
                        .properties("videoUrl", p -> p.keyword(k -> k))
                        .properties("categoryId", p -> p.long_(l -> l))
                        .properties("categoryName", p -> p.keyword(k -> k))
                        .properties("categoryIds", p -> p.long_(l -> l))
                        .properties("categoryNames", p -> p.keyword(k -> k))
                        .properties("gameTags", p -> p.object(o -> o
                                .properties("appId", g -> g.long_(l -> l))
                                .properties("name", g -> g.text(t -> t.analyzer("ik_max_word").searchAnalyzer("ik_smart")))
                                .properties("headerImage", g -> g.keyword(k -> k))))
                        .properties("status", p -> p.integer(i -> i))
                        .properties("publishedTime", p -> p.date(d -> d))
                        .properties("createTime", p -> p.date(d -> d))
                        .properties("updateTime", p -> p.date(d -> d))
                        .properties("embedding", p -> p.denseVector(v -> v
                                .dims(SearchConstants.EMBEDDING_DIMS)
                                .index(true)
                                .similarity("cosine")))
                )
                .build();
    }

    private CreateIndexRequest suggestIndexRequest(CreateIndexRequest.Builder builder) {
        return builder
                .settings(s -> s
                        .numberOfShards(String.valueOf(indexProperties.getSuggestShards()))
                        .numberOfReplicas(String.valueOf(indexProperties.getSuggestReplicas()))
                )
                .mappings(m -> m
                        .properties("id", p -> p.long_(l -> l))
                        .properties("suggest", p -> p.text(t -> t.analyzer("ik_max_word").searchAnalyzer("ik_smart")
                                .fields("keyword", f -> f.keyword(k -> k.ignoreAbove(128)))))
                        .properties("suggestNgram", p -> p.text(t -> t.analyzer("ik_max_word").searchAnalyzer("ik_smart")))
                        .properties("termId", p -> p.long_(l -> l))
                        .properties("weight", p -> p.integer(i -> i))
                        .properties("sourceType", p -> p.keyword(k -> k))
                        .properties("sourceArticleId", p -> p.long_(l -> l))
                )
                .build();
    }

    private CreateIndexRequest gameIndexRequest(CreateIndexRequest.Builder builder) {
        return builder
                .settings(s -> s.numberOfShards(String.valueOf(indexProperties.getGameShards()))
                        .numberOfReplicas(String.valueOf(indexProperties.getGameReplicas())))
                .mappings(m -> m
                        .properties("appId", p -> p.long_(l -> l))
                        .properties("name", p -> p.text(t -> t.analyzer("ik_max_word")
                                .searchAnalyzer("ik_smart")
                                .fields("keyword", f -> f.keyword(k -> k.ignoreAbove(256)))))
                        .properties("nameZh", p -> p.text(t -> t.analyzer("ik_max_word")
                                .searchAnalyzer("ik_smart")))
                        .properties("nameEn", p -> p.text(t -> t.analyzer("ik_max_word")
                                .searchAnalyzer("ik_smart")))
                        .properties("aliases", p -> p.text(t -> t.analyzer("ik_max_word")
                                .searchAnalyzer("ik_smart")))
                        .properties("shortDescription", p -> p.text(t -> t.analyzer("ik_max_word")
                                .searchAnalyzer("ik_smart")))
                        .properties("developers", p -> p.keyword(k -> k))
                        .properties("publishers", p -> p.keyword(k -> k))
                        .properties("genres", p -> p.keyword(k -> k))
                        .properties("coverUrl", p -> p.keyword(k -> k))
                        .properties("releaseDate", p -> p.keyword(k -> k))
                        .properties("steamReviewScore", p -> p.integer(i -> i))
                        .properties("steamReviewCount", p -> p.integer(i -> i))
                        .properties("avgScore", p -> p.double_(d -> d))
                        .properties("reviewCount", p -> p.integer(i -> i))
                        .properties("discussCount", p -> p.integer(i -> i))
                        .properties("price", p -> p.object(o -> o))
                        .properties("status", p -> p.integer(i -> i))
                        .properties("detailReady", p -> p.boolean_(b -> b))
                        .properties("updatedAt", p -> p.date(d -> d)))
                .build();
    }

    private void ensureArticleEmbeddingMapping() {
        try {
            if (!indexExists(SearchConstants.ARTICLE_INDEX)) {
                return;
            }
            elasticsearchClient.indices().putMapping(m -> m
                    .index(SearchConstants.ARTICLE_INDEX)
                    .properties("embedding", p -> p.denseVector(v -> v
                            .dims(SearchConstants.EMBEDDING_DIMS)
                            .index(true)
                            .similarity("cosine")))
                    .properties("postType", p -> p.integer(i -> i))
                    .properties("refArticleId", p -> p.keyword(k -> k))
                    .properties("refArticle", p -> p.object(o -> o
                            .properties("id", r -> r.keyword(k -> k))
                            .properties("title", r -> r.text(t -> t.analyzer("ik_max_word").searchAnalyzer("ik_smart")))
                            .properties("summary", r -> r.text(t -> t.analyzer("ik_max_word").searchAnalyzer("ik_smart")))
                            .properties("coverUrl", r -> r.keyword(k -> k))
                            .properties("videoUrl", r -> r.keyword(k -> k))
                            .properties("postType", r -> r.integer(i -> i))
                            .properties("authorAccountId", r -> r.long_(l -> l))
                            .properties("username", r -> r.keyword(k -> k))
                            .properties("avatar", r -> r.keyword(k -> k))))
                    .properties("videoUrl", p -> p.keyword(k -> k))
                    .properties("gameTags", p -> p.object(o -> o
                            .properties("appId", g -> g.long_(l -> l))
                            .properties("name", g -> g.text(t -> t.analyzer("ik_max_word").searchAnalyzer("ik_smart")))
                            .properties("headerImage", g -> g.keyword(k -> k)))));
            log.info("ES文章索引 embedding mapping 已确认: {}", SearchConstants.ARTICLE_INDEX);
        } catch (ElasticsearchException e) {
            log.warn("ES文章索引 embedding mapping 更新跳过: {}", e.getMessage());
        } catch (IOException e) {
            log.warn("ES文章索引 embedding mapping 更新失败: {}", e.getMessage());
        }
    }
}
