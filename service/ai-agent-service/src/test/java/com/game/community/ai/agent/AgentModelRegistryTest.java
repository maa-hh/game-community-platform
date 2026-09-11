package com.game.community.ai.agent;

import com.game.community.ai.config.AgentModelProperties;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertSame;

class AgentModelRegistryTest {

    /** 验证同一 provider/model 复用 ChatClient，且创建过程不依赖自动配置。 */
    @Test
    void cachesSpringAiClientByProviderAndModel() {
        AgentModelProperties properties = new AgentModelProperties();
        AgentModelProperties.Provider provider = new AgentModelProperties.Provider();
        provider.setBaseUrl("http://localhost:18080");
        provider.setApiKey("test-key");
        provider.setChatModel("test-chat");
        provider.setTextModerationModel("test-text");
        provider.setImageModerationModel("test-image");
        properties.setProviders(Map.of("test", provider));
        properties.setDefaultProvider("test");
        AgentModelRegistry registry = new AgentModelRegistry(properties);

        assertSame(registry.textModeration(null).client(), registry.textModeration("test").client());
    }
}
