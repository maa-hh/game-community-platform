package com.game.community.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/** OpenAI 兼容模型供应商配置。 */
@Data
@Component
@ConfigurationProperties(prefix = "ai-agent.models")
public class AgentModelProperties {

    private String defaultProvider = "deepseek";

    private Map<String, Provider> providers = new LinkedHashMap<>();

    @Data
    public static class Provider {

        private boolean enabled = true;

        private String baseUrl;

        private String completionsPath = "/v1/chat/completions";

        private String apiKey;

        private String chatModel;

        private String textModerationModel;

        private String imageModerationModel;

        private Double temperature = 0.1d;
    }
}
