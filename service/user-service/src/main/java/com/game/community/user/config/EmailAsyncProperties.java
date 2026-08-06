package com.game.community.user.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "email.async")
public class EmailAsyncProperties {

    private int corePoolSize = 2;

    private int maxPoolSize = 4;

    private int queueCapacity = 100;

    private int keepAliveSeconds = 60;

    private String threadNamePrefix = "email-";

    /** 等待实际发送完成的超时秒数 */
    private int sendTimeoutSeconds = 15;
}
