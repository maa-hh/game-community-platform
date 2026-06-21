package com.game.community.game;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@MapperScan("com.game.community.game.mapper")
@EnableFeignClients(basePackages = "com.game.community.feign")
@SpringBootApplication
public class GameAccountServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(GameAccountServiceApplication.class, args);
    }
}
