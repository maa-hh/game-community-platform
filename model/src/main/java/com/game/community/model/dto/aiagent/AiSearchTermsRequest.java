package com.game.community.model.dto.aiagent;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 内部 AI 搜索词扩展请求。 */
@Data
public class AiSearchTermsRequest {

    @NotBlank
    private String title;

    private String summary;

    private String content;

    /** 为空时使用 AI 能力服务配置的默认 Chat provider。 */
    private String provider;

    private Integer maxTerms = 5;
}
