package com.game.community.steam;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.game.community.feign")
@MapperScan("com.game.community.steam.mapper")
@SpringBootApplication(scanBasePackages = "com.game.community")
public class SteamServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SteamServiceApplication.class, args);
    }
}
