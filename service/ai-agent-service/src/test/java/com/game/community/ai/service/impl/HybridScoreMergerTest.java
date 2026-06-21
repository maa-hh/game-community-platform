package com.game.community.ai.service.impl;

import com.game.community.model.vo.aiagent.AiKnowledgeSearchHitVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HybridScoreMergerTest {

    @Test
    void shouldBoostDualHitSegments() {
        AiKnowledgeSearchHitVO bm25A = new AiKnowledgeSearchHitVO();
        bm25A.setSegmentId("a");
        bm25A.setFinalScore(10F);
        AiKnowledgeSearchHitVO bm25B = new AiKnowledgeSearchHitVO();
        bm25B.setSegmentId("b");
        bm25B.setFinalScore(9F);

        AiKnowledgeSearchHitVO semanticA = new AiKnowledgeSearchHitVO();
        semanticA.setSegmentId("a");
        semanticA.setFinalScore(0.8F);
        AiKnowledgeSearchHitVO semanticC = new AiKnowledgeSearchHitVO();
        semanticC.setSegmentId("c");
        semanticC.setFinalScore(0.7F);

        List<AiKnowledgeSearchHitVO> merged = HybridScoreMerger.merge(List.of(bm25A, bm25B), List.of(semanticA, semanticC), 3);

        assertThat(merged).hasSize(3);
        assertThat(merged.get(0).getSegmentId()).isEqualTo("a");
        assertThat(merged.get(0).getFinalScore()).isGreaterThan(merged.get(1).getFinalScore());
    }
}
