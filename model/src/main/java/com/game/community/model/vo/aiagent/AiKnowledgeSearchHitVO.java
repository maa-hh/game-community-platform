package com.game.community.model.vo.aiagent;

import lombok.Data;

import java.io.Serializable;

@Data
public class AiKnowledgeSearchHitVO implements Serializable {

    private String segmentId;

    private Long documentId;

    private String title;

    private String contentPreview;

    private Integer segmentOrder;

    private Float bm25Score;

    private Float semanticScore;

    private Float finalScore;
}
