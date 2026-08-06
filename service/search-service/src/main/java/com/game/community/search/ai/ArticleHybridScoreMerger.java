package com.game.community.search.ai;

import com.game.community.common.constant.aiagent.AiAgentConstants;
import com.game.community.model.elasticsearch.ArticleDocument;
import lombok.Data;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ArticleHybridScoreMerger {

    private ArticleHybridScoreMerger() {
    }

    @Data
    public static class ArticleHybridHit {
        private Long articleId;
        private ArticleDocument document;
        private Float bm25Score;
        private Float semanticScore;
        private Float finalScore;
    }

    public static List<ArticleHybridHit> merge(List<ArticleHybridHit> bm25Hits,
                                               List<ArticleHybridHit> semanticHits,
                                               int topK) {
        Map<Long, ArticleHybridHit> merged = new LinkedHashMap<>();
        normalize(bm25Hits, true).forEach(hit -> merged.put(hit.getArticleId(), hit));
        for (ArticleHybridHit semanticHit : normalize(semanticHits, false)) {
            merged.merge(semanticHit.getArticleId(), semanticHit, (left, right) -> {
                left.setSemanticScore(right.getSemanticScore());
                if (left.getDocument() == null) {
                    left.setDocument(right.getDocument());
                }
                return left;
            });
        }
        for (ArticleHybridHit hit : merged.values()) {
            float bm25 = hit.getBm25Score() == null ? 0F : hit.getBm25Score();
            float semantic = hit.getSemanticScore() == null ? 0F : hit.getSemanticScore();
            float bonus = bm25 > 0F && semantic > 0F ? AiAgentConstants.DUAL_HIT_BONUS : 0F;
            hit.setFinalScore(AiAgentConstants.BM25_WEIGHT * bm25
                    + AiAgentConstants.VECTOR_WEIGHT * semantic
                    + bonus);
        }
        return merged.values().stream()
                .sorted(Comparator.comparing(ArticleHybridHit::getFinalScore).reversed())
                .limit(topK)
                .toList();
    }

    public static ArticleHybridHit toHit(ArticleDocument document, float score) {
        ArticleHybridHit hit = new ArticleHybridHit();
        hit.setArticleId(document.getId());
        hit.setDocument(document);
        hit.setFinalScore(score);
        return hit;
    }

    private static List<ArticleHybridHit> normalize(List<ArticleHybridHit> hits, boolean bm25) {
        if (hits == null || hits.isEmpty()) {
            return new ArrayList<>();
        }
        float min = hits.stream().map(ArticleHybridHit::getFinalScore).min(Float::compareTo).orElse(0F);
        float max = hits.stream().map(ArticleHybridHit::getFinalScore).max(Float::compareTo).orElse(min);
        float delta = max - min;
        List<ArticleHybridHit> normalized = new ArrayList<>(hits.size());
        for (ArticleHybridHit hit : hits) {
            float value = delta <= 0.0001f ? 1F : (hit.getFinalScore() - min) / delta;
            ArticleHybridHit copy = new ArticleHybridHit();
            copy.setArticleId(hit.getArticleId());
            copy.setDocument(hit.getDocument());
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
