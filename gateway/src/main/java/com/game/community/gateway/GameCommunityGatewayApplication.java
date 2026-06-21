package com.game.community.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 网关服务启动类
 */
@EnableDiscoveryClient
@SpringBootApplication(scanBasePackages = "com.game.community.gateway", exclude = DataSourceAutoConfiguration.class)
public class GameCommunityGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GameCommunityGatewayApplication.class, args);
    }
}
