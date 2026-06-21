package com.game.community.ai.service.impl;

import com.game.community.ai.model.KnowledgeSegmentDocument;
import com.game.community.common.constant.aiagent.AiAgentConstants;
import com.game.community.model.vo.aiagent.AiKnowledgeSearchHitVO;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class HybridScoreMerger {

    private HybridScoreMerger() {
    }

    static List<AiKnowledgeSearchHitVO> merge(List<AiKnowledgeSearchHitVO> bm25Hits,
                                              List<AiKnowledgeSearchHitVO> semanticHits,
                                              int topK) {
        Map<String, AiKnowledgeSearchHitVO> merged = new LinkedHashMap<>();
        List<AiKnowledgeSearchHitVO> normalizedBm25 = normalize(bm25Hits, true);
        List<AiKnowledgeSearchHitVO> normalizedSemantic = normalize(semanticHits, false);
        normalizedBm25.forEach(hit -> merged.put(hit.getSegmentId(), hit));
        for (AiKnowledgeSearchHitVO semanticHit : normalizedSemantic) {
            merged.merge(semanticHit.getSegmentId(), semanticHit, (left, right) -> {
                left.setSemanticScore(right.getSemanticScore());
                return left;
            });
        }
        for (AiKnowledgeSearchHitVO hit : merged.values()) {
            float bm25 = hit.getBm25Score() == null ? 0F : hit.getBm25Score();
            float semantic = hit.getSemanticScore() == null ? 0F : hit.getSemanticScore();
            float bonus = bm25 > 0F && semantic > 0F ? AiAgentConstants.DUAL_HIT_BONUS : 0F;
            hit.setFinalScore(AiAgentConstants.BM25_WEIGHT * bm25 + AiAgentConstants.VECTOR_WEIGHT * semantic + bonus);
        }
        return merged.values().stream()
                .sorted(Comparator.comparing(AiKnowledgeSearchHitVO::getFinalScore).reversed())
                .limit(topK)
                .toList();
    }

    static AiKnowledgeSearchHitVO toHit(KnowledgeSegmentDocument source, Float score) {
        AiKnowledgeSearchHitVO hit = new AiKnowledgeSearchHitVO();
        hit.setSegmentId(source.getSegmentId());
        hit.setDocumentId(source.getDocumentId());
        hit.setTitle(source.getTitle());
        hit.setContentPreview(source.getContentPreview());
        hit.setSegmentOrder(source.getSegmentOrder());
        hit.setFinalScore(score);
        return hit;
    }

    private static List<AiKnowledgeSearchHitVO> normalize(List<AiKnowledgeSearchHitVO> hits, boolean bm25) {
        if (hits == null || hits.isEmpty()) {
            return new ArrayList<>();
        }
        float min = hits.stream().map(AiKnowledgeSearchHitVO::getFinalScore).min(Float::compareTo).orElse(0F);
        float max = hits.stream().map(AiKnowledgeSearchHitVO::getFinalScore).max(Float::compareTo).orElse(min);
        float delta = max - min;
        List<AiKnowledgeSearchHitVO> normalized = new ArrayList<>(hits.size());
        for (AiKnowledgeSearchHitVO hit : hits) {
            float value = delta <= 0.0001f ? 1F : (hit.getFinalScore() - min) / delta;
            AiKnowledgeSearchHitVO copy = new AiKnowledgeSearchHitVO();
            copy.setSegmentId(hit.getSegmentId());
            copy.setDocumentId(hit.getDocumentId());
            copy.setTitle(hit.getTitle());
            copy.setContentPreview(hit.getContentPreview());
            copy.setSegmentOrder(hit.getSegmentOrder());
            if (bm25) {
                copy.setBm25Score(value);
            } else {
                copy.setSemanticScore(value);
            }
            normalized.add(copy);
        }
        return normalized;
    }
}
