package com.game.community.user.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 邮件异步执行器的线程数、队列容量和线程命名配置。 */
@Data
@ConfigurationProperties(prefix = "email.async")
public class EmailAsyncProperties {

    private int corePoolSize = 2;

    private int maxPoolSize = 4;

    private int queueCapacity = 100;

    private int keepAliveSeconds = 60;

    private String threadNamePrefix = "email-";
}
