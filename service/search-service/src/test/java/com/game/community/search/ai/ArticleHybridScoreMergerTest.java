package com.game.community.search.ai;

import com.game.community.model.elasticsearch.ArticleDocument;
import com.game.community.search.ai.ArticleHybridScoreMerger.ArticleHybridHit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArticleHybridScoreMergerTest {

    @Test
    void shouldMergeBm25AndSemanticHitsByArticleId() {
        ArticleDocument docA = article(1L, "A");
        ArticleDocument docB = article(2L, "B");
        ArticleDocument docC = article(3L, "C");

        ArticleHybridHit bm25A = hit(docA, 10F);
        ArticleHybridHit bm25B = hit(docB, 9F);
        ArticleHybridHit semanticA = hit(docA, 0.8F);
        ArticleHybridHit semanticC = hit(docC, 0.7F);

        List<ArticleHybridHit> merged = ArticleHybridScoreMerger.merge(
                List.of(bm25A, bm25B),
                List.of(semanticA, semanticC),
                3);

        assertEquals(3, merged.size());
        assertEquals(1L, merged.get(0).getArticleId());
        assertTrue(merged.get(0).getFinalScore() > merged.get(1).getFinalScore());
    }

    private static ArticleDocument article(Long id, String title) {
        ArticleDocument document = new ArticleDocument();
        document.setId(id);
        document.setTitle(title);
        return document;
    }

    private static ArticleHybridHit hit(ArticleDocument document, float score) {
        ArticleHybridHit hit = ArticleHybridScoreMerger.toHit(document, score);
        hit.setFinalScore(score);
        return hit;
    }
}
