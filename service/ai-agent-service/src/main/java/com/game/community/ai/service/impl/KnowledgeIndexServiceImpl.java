package com.game.community.ai.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.KnnQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.CountResponse;
import co.elastic.clients.elasticsearch.core.DeleteByQueryRequest;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import com.game.community.ai.config.AiAgentProperties;
import com.game.community.ai.model.KnowledgeSegmentDocument;
import com.game.community.ai.service.KnowledgeIndexService;
import com.game.community.common.constant.aiagent.AiAgentConstants;
import com.game.community.model.entity.aiagent.AiKnowledgeDocument;
import com.game.community.model.vo.aiagent.AiKnowledgeStatsVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeIndexServiceImpl implements KnowledgeIndexService {

    private final ElasticsearchClient elasticsearchClient;
    private final AiAgentProperties properties;
    private final KnowledgeSegmentSplitter splitter;
    private final SimpleDashScopeClient dashScopeClient;

    @PostConstruct
    public void initIndex() {
        try {
            boolean exists = elasticsearchClient.indices()
                    .exists(ExistsRequest.of(e -> e.index(properties.getKnowledgeIndex())))
                    .value();
            if (exists) {
                return;
            }
            elasticsearchClient.indices().create(CreateIndexRequest.of(c -> c
                    .index(properties.getKnowledgeIndex())
                    .settings(s -> s.numberOfShards("1").numberOfReplicas("0"))
                    .mappings(m -> m
                            .properties("segmentId", p -> p.keyword(k -> k))
                            .properties("documentId", p -> p.long_(l -> l))
                            .properties("title", p -> p.text(t -> t.analyzer("ik_max_word").searchAnalyzer("ik_smart")))
                            .properties("content", p -> p.text(t -> t.analyzer("ik_max_word").searchAnalyzer("ik_smart")))
                            .properties("contentPreview", p -> p.text(t -> t.analyzer("ik_max_word").searchAnalyzer("ik_smart")))
                            .properties("sourceName", p -> p.keyword(k -> k))
                            .properties("segmentOrder", p -> p.integer(i -> i))
                            .properties("status", p -> p.integer(i -> i))
                            .properties("createdAt", p -> p.date(d -> d))
                            .properties("embedding", p -> p.denseVector(v -> v.dims(1024).index(true).similarity("cosine")))
                    )));
        } catch (IOException e) {
            throw new IllegalStateException("初始化 AI 知识索引失败", e);
        }
    }

    @Override
    public int indexDocument(AiKnowledgeDocument document, String content) {
        try {
            deleteDocumentSegments(document.getId());
            List<String> segments = splitter.split(content);
            List<BulkOperation> operations = new ArrayList<>(segments.size());
            for (int i = 0; i < segments.size(); i++) {
                String segment = segments.get(i);
                KnowledgeSegmentDocument source = new KnowledgeSegmentDocument();
                source.setSegmentId(document.getId() + "-" + UUID.randomUUID());
                source.setDocumentId(document.getId());
                source.setTitle(document.getTitle());
                source.setContent(segment);
                source.setContentPreview(segment.length() > 120 ? segment.substring(0, 120) + "..." : segment);
                source.setSourceName(document.getSourceName());
                source.setSegmentOrder(i);
                source.setStatus(AiAgentConstants.DOCUMENT_STATUS_ACTIVE);
                source.setCreatedAt(LocalDateTime.now());
                source.setEmbedding(dashScopeClient.embedding(segment));
                operations.add(BulkOperation.of(b -> b.index(iop -> iop
                        .index(properties.getKnowledgeIndex())
                        .id(source.getSegmentId())
                        .document(source))));
            }
            if (!operations.isEmpty()) {
                elasticsearchClient.bulk(b -> b.index(properties.getKnowledgeIndex()).operations(operations));
            }
            return operations.size();
        } catch (IOException e) {
            throw new IllegalStateException("写入 AI 知识索引失败", e);
        }
    }

    @Override
    public void deleteDocumentSegments(Long documentId) {
        try {
            elasticsearchClient.deleteByQuery(DeleteByQueryRequest.of(d -> d
                    .index(properties.getKnowledgeIndex())
                    .query(q -> q.term(t -> t.field("documentId").value(documentId)))));
        } catch (IOException e) {
            throw new IllegalStateException("删除 AI 知识切片失败", e);
        }
    }

    @Override
    public AiKnowledgeStatsVO stats() {
        try {
            CountResponse segmentCount = elasticsearchClient.count(c -> c.index(properties.getKnowledgeIndex()));
            return new AiKnowledgeStatsVO(0L, 0L, segmentCount.count());
        } catch (IOException e) {
            throw new IllegalStateException("获取 AI 知识索引统计失败", e);
        }
    }
}
