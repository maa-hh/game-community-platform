package com.game.community.recommend;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.annotation.EnableKafka;
import com.game.community.utils.RedisUtils;
import com.game.community.common.exception.GlobalExceptionHandler;

@EnableKafka
@EnableFeignClients(basePackages = "com.game.community.feign")
@EnableDiscoveryClient
@MapperScan("com.game.community.recommend.mapper")
@Import({RedisUtils.class, GlobalExceptionHandler.class})
@SpringBootApplication
public class RecommendServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RecommendServiceApplication.class, args);
    }
}
