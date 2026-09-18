package com.game.community.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

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

    /** Redis 集群级 Provider 并发上限，所有 AI 实例共享。 */
    private int globalMaxConcurrentModelCalls = 32;

    /** Redis 并发许可租约秒数，需覆盖 Provider 最大读取超时。 */
    private long globalPermitLeaseSeconds = 120;

    /** 单张图片最大字节数，防止 Base64 请求造成堆内存和 Token 放大。 */
    private int maxImageBytes = 5 * 1024 * 1024;

    /** 单篇文章最多提交给模型的有效图片数量。 */
    private int maxArticleImages = 9;

    /** 是否允许模型供应商直接读取远程图片 URL，默认关闭。 */
    private boolean allowRemoteImageUrls = false;

    /** 允许供应商读取图片的精确 Host 白名单。 */
    private List<String> allowedImageHosts = new ArrayList<>();
}
