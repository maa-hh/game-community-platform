package com.game.community.utils.audit;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * DashScope 审核配置。
 */
@Data
@ConfigurationProperties(prefix = "audit.dashscope")
public class DashScopeAuditProperties {

    /**
     * 文本审核模型，未配置时回退到 Spring AI DashScope 默认 chat model。
     */
    private String textModel;

    /**
     * 图片审核模型，建议配置为支持多模态的视觉模型。
     */
    private String imageModel;

    /**
     * DashScope OpenAI 兼容模式视觉理解地址。
     */
    private String imageCompatibleEndpoint = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";

    private Double temperature = 0.1d;

    /**
     * 当 DashScope 接口不可用时，是否按本地规则放行。
     */
    private boolean failOpenOnUnavailable = false;
}
