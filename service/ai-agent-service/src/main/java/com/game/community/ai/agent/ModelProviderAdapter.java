package com.game.community.ai.agent;

import com.game.community.ai.config.AgentModelProperties;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;

/** Provider 协议适配器，隔离供应商 SDK 和审核业务。 */
public interface ModelProviderAdapter {

    /** 返回配置中的协议标识。 */
    String protocol();

    /** 根据供应商配置创建一个可复用的 ChatClient。 */
    ChatClient createClient(String providerId, AgentModelProperties.Provider provider, String model);

    /** 创建当前协议的 embedding 客户端；不支持时由注册中心明确拒绝。 */
    default EmbeddingClient createEmbeddingClient(String providerId,
                                                  AgentModelProperties.Provider provider,
                                                  String model) {
        throw new IllegalStateException("模型供应商协议不支持 embedding: " + protocol());
    }

    @FunctionalInterface
    interface EmbeddingClient {

        List<Float> embed(String text);
    }
}
