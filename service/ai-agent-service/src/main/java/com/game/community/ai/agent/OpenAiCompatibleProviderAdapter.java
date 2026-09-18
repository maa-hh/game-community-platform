package com.game.community.ai.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.game.community.ai.config.AgentModelProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
/** OpenAI Compatible 协议适配器，兼容 DeepSeek 等同协议供应商。 */
@Slf4j
@Component
public class OpenAiCompatibleProviderAdapter implements ModelProviderAdapter {

    /** OpenAI Compatible 配置协议名称。 */
    public static final String PROTOCOL = "openai-compatible";

    @Override
    public String protocol() {
        return PROTOCOL;
    }

    /** 构造 OpenAI Compatible ChatClient，供应商差异不泄漏到审核流程。 */
    @Override
    public ChatClient createClient(String providerId, AgentModelProperties.Provider provider, String model) {
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(provider.getBaseUrl())
                .completionsPath(resolvePath(provider.getBaseUrl(), provider.getCompletionsPath()))
                .apiKey(provider.getApiKey())
                .restClientBuilder(RestClient.builder().requestFactory(requestFactory(provider)))
                .build();
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(model)
                .temperature(provider.getTemperature())
                .build();
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(options)
                // 审核请求不自动重试，避免供应商已接收但响应超时时重复计费。
                .retryTemplate(RetryTemplate.builder().maxAttempts(1).build())
                .build();
        log.info("创建 OpenAI Compatible 模型客户端: provider={}, model={}", providerId, model);
        return ChatClient.builder(chatModel).build();
    }

    @Override
    public EmbeddingClient createEmbeddingClient(String providerId,
                                                 AgentModelProperties.Provider provider,
                                                 String model) {
        RestClient restClient = RestClient.builder()
                .baseUrl(provider.getBaseUrl())
                .requestFactory(requestFactory(provider))
                .build();
        String embeddingsPath = resolvePath(provider.getBaseUrl(), provider.getEmbeddingsPath());
        log.info("创建 OpenAI Compatible Embedding 客户端: provider={}, model={}", providerId, model);
        return text -> {
            try {
                JsonNode response = restClient.post()
                        .uri(embeddingsPath)
                        .header("Authorization", "Bearer " + provider.getApiKey())
                        .body(Map.of("model", model, "input", text))
                        .retrieve()
                        .body(JsonNode.class);
                JsonNode embedding = response == null || !response.path("data").isArray()
                        || response.path("data").isEmpty()
                        ? null : response.path("data").get(0).path("embedding");
                if (embedding == null || !embedding.isArray() || embedding.isEmpty()) {
                    throw new IllegalStateException("embedding 服务返回为空");
                }
                List<Float> vector = new ArrayList<>(embedding.size());
                for (JsonNode node : embedding) {
                    vector.add(node.floatValue());
                }
                return vector;
            } catch (RestClientException e) {
                throw new IllegalStateException("embedding 服务请求失败", e);
            }
        };
    }

    /** 为每个 Provider 配置独立连接和读取超时，避免慢供应商占满工作线程。 */
    private JdkClientHttpRequestFactory requestFactory(AgentModelProperties.Provider provider) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(100, provider.getConnectTimeoutMs())))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(Duration.ofMillis(Math.max(100, provider.getReadTimeoutMs())));
        return factory;
    }

    /** 避免供应商 base-url 已含 /v1 时形成 /v1/v1/chat/completions。 */
    private String resolvePath(String baseUrl, String configuredPath) {
        String path = StringUtils.hasText(configuredPath) ? configuredPath.trim() : "/v1/chat/completions";
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        String normalizedBaseUrl = baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        if (normalizedBaseUrl.endsWith("/v1") && path.startsWith("/v1/")) {
            return path.substring(3);
        }
        return path;
    }
}
