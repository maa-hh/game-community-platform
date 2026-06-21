package com.game.community.model.dto.aiagent;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class KnowledgeSearchDebugRequest {

    @NotBlank(message = "query 不能为空")
    private String query;

    private Integer topK = 6;
}
