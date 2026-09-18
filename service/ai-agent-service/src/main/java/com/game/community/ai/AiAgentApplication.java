package com.game.community.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AiAgentApplication {

    /** 启动 AI 审核微服务。 */
    public static void main(String[] args) {
        SpringApplication.run(AiAgentApplication.class, args);
    }
}
