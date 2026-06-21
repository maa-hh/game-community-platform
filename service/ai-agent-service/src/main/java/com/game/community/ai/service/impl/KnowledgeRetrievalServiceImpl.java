package com.game.community.ai.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.game.community.ai.config.AiAgentProperties;
import com.game.community.ai.model.KnowledgeSegmentDocument;
import com.game.community.ai.service.KnowledgeRetrievalService;
import com.game.community.model.vo.aiagent.AiKnowledgeDebugVO;
import com.game.community.model.vo.aiagent.AiKnowledgeSearchHitVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class KnowledgeRetrievalServiceImpl implements KnowledgeRetrievalService {

    private final ElasticsearchClient elasticsearchClient;
    private final AiAgentProperties properties;
    private final SimpleDashScopeClient dashScopeClient;

    @Override
    public List<AiKnowledgeSearchHitVO> retrieve(String query, Integer topK) {
        int actualTopK = topK == null || topK <= 0 ? properties.getRetrievalTopK() : topK;
        AiKnowledgeDebugVO debug = debugSearch(query, actualTopK);
        return debug.getMergedHits();
    }

    @Override
    public AiKnowledgeDebugVO debugSearch(String query, Integer topK) {
        int actualTopK = topK == null || topK <= 0 ? properties.getRetrievalTopK() : topK;
        try {
            SearchResponse<KnowledgeSegmentDocument> bm25Response = elasticsearchClient.search(s -> s
                            .index(properties.getKnowledgeIndex())
                            .size(actualTopK)
                            .query(q -> q.multiMatch(m -> m
                                    .query(query)
                                    .fields("title", "content", "contentPreview"))),
                    KnowledgeSegmentDocument.class);
            List<Float> vector = dashScopeClient.embedding(query);
            SearchResponse<KnowledgeSegmentDocument> semanticResponse = elasticsearchClient.search(s -> s
                            .index(properties.getKnowledgeIndex())
                            .size(actualTopK)
                            .knn(k -> k
                                    .field("embedding")
                                    .queryVector(vector)
                                    .k(actualTopK)
                                    .numCandidates(properties.getRetrievalCandidateK())),
                    KnowledgeSegmentDocument.class);
            List<AiKnowledgeSearchHitVO> bm25Hits = bm25Response.hits().hits().stream()
                    .filter(hit -> hit.source() != null)
                    .map(hit -> HybridScoreMerger.toHit(hit.source(), hit.score() == null ? 0F : hit.score().floatValue()))
                    .collect(Collectors.toList());
            List<AiKnowledgeSearchHitVO> semanticHits = semanticResponse.hits().hits().stream()
                    .filter(hit -> hit.source() != null)
                    .map(hit -> HybridScoreMerger.toHit(hit.source(), hit.score() == null ? 0F : hit.score().floatValue()))
                    .collect(Collectors.toList());
            AiKnowledgeDebugVO vo = new AiKnowledgeDebugVO();
            vo.setQuery(query);
            vo.setBm25Hits(bm25Hits);
            vo.setSemanticHits(semanticHits);
            vo.setMergedHits(HybridScoreMerger.merge(bm25Hits, semanticHits, actualTopK));
            return vo;
        } catch (IOException e) {
            throw new IllegalStateException("执行混合检索失败", e);
        }
    }
}
