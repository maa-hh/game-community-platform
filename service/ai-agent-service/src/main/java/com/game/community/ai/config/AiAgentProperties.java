package com.game.community.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "ai-agent")
public class AiAgentProperties {

    private String apiKey;
    private String embeddingEndpoint;
    private String chatEndpoint;
    private String embeddingModel;
    private String chatModel;
    private String knowledgeIndex;
    private Integer retrievalTopK = 6;
    private Integer retrievalCandidateK = 12;
    private Integer segmentTargetLength = 420;
    private Integer segmentOverlap = 60;
    private Integer memoryTtlHours = 72;
    private Integer maxRounds = 10;
}
