package com.game.community.danmaku;

import com.game.community.common.exception.GlobalExceptionHandler;
import com.game.community.utils.DfaAuditUtils;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Import;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.game.community.feign")
@EnableScheduling
@MapperScan("com.game.community.danmaku.mapper")
@Import({DfaAuditUtils.class, GlobalExceptionHandler.class})
@SpringBootApplication(scanBasePackages = "com.game.community.danmaku")
public class DanmakuServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DanmakuServiceApplication.class, args);
    }
}
