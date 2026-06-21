package com.game.community.content;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 内容服务启动类
 */
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.game.community.feign")
@EnableScheduling
@MapperScan("com.game.community.content.mapper")
@SpringBootApplication(scanBasePackages = "com.game.community")
public class ContentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ContentServiceApplication.class, args);
    }
}
