package com.game.community.model.dto.aiagent;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 内部 AI embedding 请求。 */
@Data
public class AiEmbeddingRequest {

    @NotBlank
    private String text;

    /** 为空时使用 AI 能力服务配置的默认 embedding provider。 */
    private String provider;
}
