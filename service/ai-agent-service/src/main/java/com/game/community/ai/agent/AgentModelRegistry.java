package com.game.community.ai.agent;

import com.game.community.ai.config.AgentModelProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import jakarta.annotation.PostConstruct;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 按 provider + model 创建并缓存 Spring AI ChatClient。
 * 新增 OpenAI 兼容模型只需要增加配置，不进入业务审核代码。
 */
@Slf4j
@Component
public class AgentModelRegistry {

    private final AgentModelProperties properties;
    private final Map<String, ModelProviderAdapter> adapters;
    private final Map<String, ChatClient> clients = new ConcurrentHashMap<>();
    private final Map<String, ModelProviderAdapter.EmbeddingClient> embeddingClients = new ConcurrentHashMap<>();

    /** 生产环境注入全部 Provider Adapter，按协议标识建立路由表。 */
    @Autowired
    public AgentModelRegistry(AgentModelProperties properties, List<ModelProviderAdapter> adapters) {
        this.properties = properties;
        this.adapters = adapters.stream().collect(Collectors.toUnmodifiableMap(
                adapter -> adapter.protocol().trim().toLowerCase(), Function.identity()));
    }

    /** 单元测试使用默认 OpenAI Compatible Adapter。 */
    public AgentModelRegistry(AgentModelProperties properties) {
        this(properties, List.of(new OpenAiCompatibleProviderAdapter()));
    }

    /** 启动时校验默认审核 Provider，避免 API Key 未加载时请求才静默降级。 */
    @PostConstruct
    public void validateDefaultProvider() {
        ProviderSnapshot provider = provider(null, properties.getDefaultProvider());
        log.info("AI 默认 Provider 配置就绪: provider={}, protocol={}, model={}, apiKeyConfigured={}, baseUrl={}",
                provider.id(), provider.protocol(), provider.textModerationModel(),
                StringUtils.hasText(provider.apiKey()), provider.baseUrl());
    }

    /** 获取通用 Chat 模型。 */
    public ModelClient chat(String providerId) {
        ProviderSnapshot provider = provider(providerId, properties.getDefaultProvider());
        return client(provider, requiredModel(provider.chatModel(), "chat-model"));
    }

    /** 获取文本审核模型。 */
    public ModelClient textModeration(String providerId) {
        ProviderSnapshot provider = provider(providerId, properties.getDefaultProvider());
        String model = StringUtils.hasText(provider.textModerationModel())
                ? provider.textModerationModel() : provider.chatModel();
        return client(provider, requiredModel(model, "text-moderation-model/chat-model"));
    }

    /** 获取图片审核模型。 */
    public ModelClient imageModeration(String providerId) {
        ProviderSnapshot provider = provider(providerId, properties.getDefaultProvider());
        return client(provider, requiredModel(provider.imageModerationModel(), "image-moderation-model"));
    }

    /** 获取并缓存指定 provider 的 embedding 客户端。 */
    public EmbeddingModelClient embedding(String providerId) {
        ProviderSnapshot provider = provider(providerId, properties.getDefaultEmbeddingProvider());
        String model = requiredModel(provider.embeddingModel(), "embedding-model");
        String cacheKey = provider.id() + ":" + model;
        ModelProviderAdapter.EmbeddingClient client = embeddingClients.computeIfAbsent(cacheKey,
                ignored -> buildEmbeddingClient(provider, model));
        return new EmbeddingModelClient(provider.id(), model, client);
    }

    /** 清空模型客户端缓存，供配置刷新后重建。 */
    public void reload() {
        clients.clear();
        embeddingClients.clear();
    }

    /** 配置中心刷新模型参数后清理旧客户端，避免继续使用旧地址或旧密钥。 */
    @EventListener(EnvironmentChangeEvent.class)
    public void onEnvironmentChange(EnvironmentChangeEvent event) {
        if (event.getKeys().stream().anyMatch(key -> key.startsWith("ai-agent.models."))) {
            reload();
            log.info("AI 模型配置已刷新，客户端缓存已清理");
        }
    }

    /** 解析默认供应商别名并校验配置。 */
    private ProviderSnapshot provider(String requestedId, String defaultProvider) {
        String providerId = StringUtils.hasText(requestedId) && !"default".equalsIgnoreCase(requestedId.trim())
                ? requestedId.trim() : defaultProvider;
        AgentModelProperties.Provider configured = properties.getProviders().get(providerId);
        if (configured == null || !configured.isEnabled()) {
            throw new IllegalArgumentException("模型供应商不存在或未启用: " + providerId);
        }
        if (!StringUtils.hasText(configured.getBaseUrl()) || !StringUtils.hasText(configured.getApiKey())) {
            throw new IllegalStateException("模型供应商缺少 base-url 或 api-key: " + providerId);
        }
        return new ProviderSnapshot(providerId, configured.getBaseUrl(), configured.getCompletionsPath(),
                configured.getApiKey(), configured.getChatModel(), configured.getEmbeddingModel(),
                configured.getTextModerationModel(), configured.getImageModerationModel(),
                configured.getTemperature(), configured.getProtocol(), configured);
    }

    /** 创建指定模型的 Spring AI 客户端并按配置组合缓存。 */
    private ModelClient client(ProviderSnapshot provider, String model) {
        String cacheKey = provider.id() + ":" + model;
        ChatClient client = clients.computeIfAbsent(cacheKey, ignored -> buildClient(provider, model));
        return new ModelClient(provider.id(), model, client, provider.configuration());
    }

    /** 根据 Provider 协议选择适配器构造模型客户端。 */
    private ChatClient buildClient(ProviderSnapshot provider, String model) {
        String protocol = StringUtils.hasText(provider.protocol())
                ? provider.protocol().trim().toLowerCase() : OpenAiCompatibleProviderAdapter.PROTOCOL;
        ModelProviderAdapter adapter = adapters.get(protocol);
        if (adapter == null) {
            throw new IllegalStateException("不支持的模型供应商协议: " + protocol);
        }
        return adapter.createClient(provider.id(), provider.configuration(), model);
    }

    private ModelProviderAdapter.EmbeddingClient buildEmbeddingClient(ProviderSnapshot provider, String model) {
        String protocol = StringUtils.hasText(provider.protocol())
                ? provider.protocol().trim().toLowerCase() : OpenAiCompatibleProviderAdapter.PROTOCOL;
        ModelProviderAdapter adapter = adapters.get(protocol);
        if (adapter == null) {
            throw new IllegalStateException("不支持的模型供应商协议: " + protocol);
        }
        return adapter.createEmbeddingClient(provider.id(), provider.configuration(), model);
    }

    /** 校验场景所需模型名。 */
    private String requiredModel(String model, String propertyName) {
        if (!StringUtils.hasText(model)) {
            throw new IllegalStateException("模型配置缺少 " + propertyName);
        }
        return model;
    }

    /** 返回已解析的 provider、模型名和可复用客户端。 */
    public record ModelClient(String provider, String model, ChatClient client,
                              AgentModelProperties.Provider configuration) {
    }

    public record EmbeddingModelClient(String provider, String model,
                                       ModelProviderAdapter.EmbeddingClient client) {
    }

    private record ProviderSnapshot(String id, String baseUrl, String completionsPath, String apiKey,
                                    String chatModel, String embeddingModel, String textModerationModel,
                                    String imageModerationModel, Double temperature, String protocol,
                                    AgentModelProperties.Provider configuration) {
    }
}
