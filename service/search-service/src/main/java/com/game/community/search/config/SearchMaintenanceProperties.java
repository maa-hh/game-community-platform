package com.game.community.search.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "search.maintenance")
public class SearchMaintenanceProperties {

    /** 建议词冷词清理 cron，允许按环境调整时区和执行窗口。 */
    private String suggestCleanupCron = "0 0 3 * * ?";

    /** MySQL named lock 等待秒数，0 表示已有实例执行时直接跳过。 */
    private int lockTimeoutSeconds = 0;
}
