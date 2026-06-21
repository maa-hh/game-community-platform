package com.game.community.ai.model;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class KnowledgeSegmentDocument {

    private String segmentId;

    private Long documentId;

    private String title;

    private String content;

    private String contentPreview;

    private String sourceName;

    private Integer segmentOrder;

    private Integer status;

    private LocalDateTime createdAt;

    private List<Float> embedding;
}
