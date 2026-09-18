package com.game.community.search.ai;

import com.game.community.common.constant.search.SearchConstants;
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
        rankNormalize(bm25Hits, true).forEach(hit -> merged.put(hit.getArticleId(), hit));
        for (ArticleHybridHit semanticHit : rankNormalize(semanticHits, false)) {
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
            float bonus = bm25 > 0F && semantic > 0F ? SearchConstants.HYBRID_DUAL_HIT_BONUS : 0F;
            hit.setFinalScore(SearchConstants.HYBRID_BM25_WEIGHT * bm25
                    + SearchConstants.HYBRID_VECTOR_WEIGHT * semantic
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

    /**
     * BM25 与 kNN 的原始 score 量纲不同，不能直接按原始分数加权。
     * 使用 rank-based reciprocal score 做归一化，使融合结果不依赖具体索引分片或查询词。
     */
    private static List<ArticleHybridHit> rankNormalize(List<ArticleHybridHit> hits, boolean bm25) {
        if (hits == null || hits.isEmpty()) {
            return new ArrayList<>();
        }
        List<ArticleHybridHit> ranked = hits.stream()
                .sorted(Comparator.comparing(ArticleHybridHit::getFinalScore,
                        Comparator.nullsFirst(Comparator.reverseOrder())))
                .toList();
        List<ArticleHybridHit> normalized = new ArrayList<>(hits.size());
        for (int i = 0; i < ranked.size(); i++) {
            ArticleHybridHit hit = ranked.get(i);
            float value = SearchConstants.HYBRID_RRF_K / (float) (SearchConstants.HYBRID_RRF_K + i + 1);
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
