package com.game.community.search.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "search.sync")
public class SearchSyncProperties {

    /** 启动完成后自动全量同步文章索引与建议词 */
    private boolean onStartup = true;
}
