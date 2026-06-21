package com.game.community.search.initIndex;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Slf4j
@Service
@RequiredArgsConstructor
public class InitElasticsearchIndex {

    public static final String ARTICLE_INDEX = "article_index";
    public static final String SUGGEST_INDEX = "suggest_index";

    private final ElasticsearchClient elasticsearchClient;

    public void initIndex() {
        initArticleIndex();
        initSuggestIndex();
    }

    private void initArticleIndex() {
        try {
            boolean exists = elasticsearchClient.indices()
                    .exists(ExistsRequest.of(e -> e.index(ARTICLE_INDEX)))
                    .value();
            if (exists) {
                log.info("ES文章索引已存在: {}", ARTICLE_INDEX);
                return;
            }
            elasticsearchClient.indices().create(CreateIndexRequest.of(c -> c
                    .index(ARTICLE_INDEX)
                    .settings(s -> s
                            .numberOfShards("1")
                            .numberOfReplicas("0")
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
                            .properties("categoryId", p -> p.long_(l -> l))
                            .properties("categoryName", p -> p.keyword(k -> k))
                            .properties("status", p -> p.integer(i -> i))
                            .properties("publishedTime", p -> p.date(d -> d))
                            .properties("createTime", p -> p.date(d -> d))
                            .properties("updateTime", p -> p.date(d -> d))
                    )
            ));
            log.info("ES文章索引创建成功: {}", ARTICLE_INDEX);
        } catch (IOException e) {
            throw new IllegalStateException("创建ES文章索引失败", e);
        }
    }

    private void initSuggestIndex() {
        try {
            boolean exists = elasticsearchClient.indices()
                    .exists(ExistsRequest.of(e -> e.index(SUGGEST_INDEX)))
                    .value();
            if (exists) {
                log.info("ES建议词索引已存在: {}", SUGGEST_INDEX);
                return;
            }
            elasticsearchClient.indices().create(CreateIndexRequest.of(c -> c
                    .index(SUGGEST_INDEX)
                    .settings(s -> s
                            .numberOfShards("1")
                            .numberOfReplicas("0")
                    )
                    .mappings(m -> m
                            .properties("id", p -> p.long_(l -> l))
                            .properties("suggest", p -> p.text(t -> t.analyzer("ik_max_word").searchAnalyzer("ik_smart")
                                    .fields("keyword", f -> f.keyword(k -> k.ignoreAbove(128)))))
                            .properties("suggestNgram", p -> p.text(t -> t.analyzer("ik_max_word").searchAnalyzer("ik_smart")))
                    )
            ));
            log.info("ES建议词索引创建成功: {}", SUGGEST_INDEX);
        } catch (IOException e) {
            throw new IllegalStateException("创建ES建议词索引失败", e);
        }
    }
}
