package com.game.community.ai.agent;

import com.game.community.ai.config.AgentModelProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.retry.support.RetryTemplate;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 按 provider + model 创建并缓存 Spring AI ChatClient。
 * 新增 OpenAI 兼容模型只需要增加配置，不进入业务审核代码。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentModelRegistry {

    private final AgentModelProperties properties;
    private final Map<String, ChatClient> clients = new ConcurrentHashMap<>();

    /** 获取通用对话模型。 */
    public ModelClient chat(String providerId) {
        ProviderSnapshot provider = provider(providerId);
        return client(provider, requiredModel(provider.chatModel(), "chat-model"));
    }

    /** 获取文本审核模型。 */
    public ModelClient textModeration(String providerId) {
        ProviderSnapshot provider = provider(providerId);
        String model = StringUtils.hasText(provider.textModerationModel())
                ? provider.textModerationModel() : provider.chatModel();
        return client(provider, requiredModel(model, "text-moderation-model/chat-model"));
    }

    /** 获取图片审核模型。 */
    public ModelClient imageModeration(String providerId) {
        ProviderSnapshot provider = provider(providerId);
        return client(provider, requiredModel(provider.imageModerationModel(), "image-moderation-model"));
    }

    /** 清空模型客户端缓存，供配置刷新后重建。 */
    public void reload() {
        clients.clear();
    }

    /** 解析默认供应商别名并校验配置。 */
    private ProviderSnapshot provider(String requestedId) {
        String providerId = StringUtils.hasText(requestedId) && !"default".equalsIgnoreCase(requestedId.trim())
                ? requestedId.trim() : properties.getDefaultProvider();
        AgentModelProperties.Provider configured = properties.getProviders().get(providerId);
        if (configured == null || !configured.isEnabled()) {
            throw new IllegalArgumentException("模型供应商不存在或未启用: " + providerId);
        }
        if (!StringUtils.hasText(configured.getBaseUrl()) || !StringUtils.hasText(configured.getApiKey())) {
            throw new IllegalStateException("模型供应商缺少 base-url 或 api-key: " + providerId);
        }
        return new ProviderSnapshot(providerId, configured.getBaseUrl(), configured.getCompletionsPath(),
                configured.getApiKey(), configured.getChatModel(), configured.getTextModerationModel(),
                configured.getImageModerationModel(), configured.getTemperature());
    }

    /** 创建指定模型的 Spring AI 客户端并按配置组合缓存。 */
    private ModelClient client(ProviderSnapshot provider, String model) {
        String cacheKey = provider.id() + ":" + model;
        ChatClient client = clients.computeIfAbsent(cacheKey, ignored -> buildClient(provider, model));
        return new ModelClient(provider.id(), model, client);
    }

    /** 使用 Spring AI OpenAI 兼容 API 构造模型。 */
    private ChatClient buildClient(ProviderSnapshot provider, String model) {
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(provider.baseUrl())
                .completionsPath(resolveCompletionsPath(provider.baseUrl(), provider.completionsPath()))
                .apiKey(provider.apiKey())
                .build();
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(model)
                .temperature(provider.temperature())
                .build();
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(options)
                // 审核请求不自动重试，避免供应商已接收但响应超时时重复计费；失败直接转人工。
                .retryTemplate(RetryTemplate.builder().maxAttempts(1).build())
                .build();
        log.info("创建 Spring AI 模型客户端: provider={}, model={}", provider.id(), model);
        return ChatClient.builder(chatModel).build();
    }

    /** 避免供应商 base-url 已含 /v1 时形成 /v1/v1/chat/completions。 */
    private String resolveCompletionsPath(String baseUrl, String configuredPath) {
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

    /** 校验场景所需模型名。 */
    private String requiredModel(String model, String propertyName) {
        if (!StringUtils.hasText(model)) {
            throw new IllegalStateException("模型配置缺少 " + propertyName);
        }
        return model;
    }

    public record ModelClient(String provider, String model, ChatClient client) {
    }

    private record ProviderSnapshot(String id, String baseUrl, String completionsPath, String apiKey,
                                    String chatModel, String textModerationModel,
                                    String imageModerationModel, Double temperature) {
    }
}
