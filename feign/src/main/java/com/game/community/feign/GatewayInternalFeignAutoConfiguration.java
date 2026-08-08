package com.game.community.feign;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

/** 共享 Feign 客户端的服务间调用配置。 */
@AutoConfiguration
public class GatewayInternalFeignAutoConfiguration {

    @Bean
    public GatewayInternalFeignInterceptor gatewayInternalFeignInterceptor() {
        return new GatewayInternalFeignInterceptor();
    }
}
