package com.game.community.search.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "search.ai")
public class SearchAiProperties {

    /** AI provider 总开关，默认关闭，避免未配置密钥时请求链路产生额外延迟。 */
    private boolean enabled = false;

    /** 语义向量召回开关；可通过配置中心或环境变量动态控制。 */
    private boolean semanticEnabled = false;

    private boolean hybridEnabled = false;

    private boolean aiSuggestEnabled = true;

    private String embeddingEndpoint = "https://dashscope.aliyuncs.com/compatible-mode/v1/embeddings";

    private String chatEndpoint = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";

    private String embeddingModel = "text-embedding-v4";

    private String chatModel = "qwen-plus";

    private int hybridCandidateK = 50;

    private int hybridNumCandidates = 100;

    private int connectTimeoutMs = 500;

    private int readTimeoutMs = 1500;
}
