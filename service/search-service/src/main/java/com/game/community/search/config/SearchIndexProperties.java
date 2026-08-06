package com.game.community.search.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** ES 容量参数放配置中心，不把部署规模写死在索引初始化代码中。 */
@Data
@Component
@ConfigurationProperties(prefix = "search.index")
public class SearchIndexProperties {

    private int articleShards = 3;
    private int articleReplicas = 1;
    private int suggestShards = 1;
    private int suggestReplicas = 1;
    private int gameShards = 2;
    private int gameReplicas = 1;
}
