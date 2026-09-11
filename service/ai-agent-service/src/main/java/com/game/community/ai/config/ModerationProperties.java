package com.game.community.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 审核阈值、提示词和失败策略配置。 */
@Data
@Component
@ConfigurationProperties(prefix = "ai-agent.moderation")
public class ModerationProperties {

    private int rejectMaxScore = 3;

    private int humanReviewMaxScore = 6;

    private String unavailableDecision = "HUMAN_REVIEW";

    private String sensitiveWordsPath = "classpath:moderation/sensitive-words.txt";

    private String systemPromptPath = "classpath:prompts/moderation-system.st";

    private String textPromptPath = "classpath:prompts/moderation-text-user.st";

    private String imagePromptPath = "classpath:prompts/moderation-image-user.st";

    private String articlePromptPath = "classpath:prompts/moderation-article-user.st";

    /** 单次文本输入上限，避免把超长内容直接发送给模型导致 token 溢出。 */
    private int maxTextChars = 12000;

    /** 模型并发保护阈值，超过后转人工，避免线程堆积拖垮服务。 */
    private int maxConcurrentModelCalls = 8;
}
