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

    private boolean aiSuggestEnabled = false;

    /** 内部 AI 能力服务使用的 embedding provider。 */
    private String embeddingProvider = "dashscope";

    /** 内部 AI 能力服务使用的搜索词扩展 provider。 */
    private String searchTermsProvider = "dashscope";

    private int hybridCandidateK = 50;

    private int hybridNumCandidates = 100;

}
