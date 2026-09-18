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

    private String defaultEmbeddingProvider = "dashscope";

    private Map<String, Provider> providers = new LinkedHashMap<>();

    /** 单个 OpenAI Compatible 供应商及场景模型配置。 */
    @Data
    public static class Provider {

        private boolean enabled = true;

        /** Provider 协议，默认使用 OpenAI Compatible。 */
        private String protocol = "openai-compatible";

        private String baseUrl;

        private String completionsPath = "/v1/chat/completions";

        private String embeddingsPath = "/v1/embeddings";

        private String apiKey;

        private String chatModel;

        private String embeddingModel;

        private String textModerationModel;

        private String imageModerationModel;

        private Double temperature = 0.1d;

        /** Provider 每秒允许的请求数，单实例生效。 */
        private int requestsPerSecond = 20;

        /** Provider 单实例最大并发数。 */
        private int maxConcurrentCalls = 8;

        /** Provider 熔断失败率阈值。 */
        private float circuitFailureRateThreshold = 50F;

        /** Provider 熔断开始统计所需的最小调用数。 */
        private int circuitMinimumNumberOfCalls = 10;

        /** Provider 熔断滑动窗口大小。 */
        private int circuitSlidingWindowSize = 20;

        /** Provider 熔断打开后的恢复等待秒数。 */
        private long circuitOpenSeconds = 30;

        /** Provider TCP 连接超时毫秒数。 */
        private int connectTimeoutMs = 2000;

        /** Provider 响应读取超时毫秒数。 */
        private int readTimeoutMs = 10000;
    }
}
