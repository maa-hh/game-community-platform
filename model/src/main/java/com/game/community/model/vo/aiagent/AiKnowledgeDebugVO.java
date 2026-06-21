package com.game.community.model.vo.aiagent;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class AiKnowledgeDebugVO implements Serializable {

    private String query;

    private List<AiKnowledgeSearchHitVO> bm25Hits;

    private List<AiKnowledgeSearchHitVO> semanticHits;

    private List<AiKnowledgeSearchHitVO> mergedHits;
}
