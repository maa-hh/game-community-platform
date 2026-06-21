package com.game.community.user.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 审核异步线程池配置
 */
@Data
@ConfigurationProperties(prefix = "audit.async")
public class AuditAsyncProperties {

    private int corePoolSize = 2;

    private int maxPoolSize = 4;

    private int queueCapacity = 200;

    private int keepAliveSeconds = 60;

    private String threadNamePrefix = "audit-";
}
