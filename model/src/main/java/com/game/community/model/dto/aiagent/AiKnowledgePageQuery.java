package com.game.community.model.dto.aiagent;

import lombok.Data;

@Data
public class AiKnowledgePageQuery {

    private Long page = 1L;

    private Long size = 10L;

    private String keyword;

    private Integer status;
}
