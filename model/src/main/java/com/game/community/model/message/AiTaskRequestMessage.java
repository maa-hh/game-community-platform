package com.game.community.model.message;

import lombok.Data;

import java.io.Serializable;

/**
 * AI 任务请求消息。requestPayload 和 contextPayload 使用 JSON 字符串，避免公共消息模块耦合各业务服务 DTO。
 */
@Data
public class AiTaskRequestMessage implements Serializable {

    public static final String MODERATION = "MODERATION";
    public static final String EMBEDDING = "EMBEDDING";
    public static final String SEARCH_TERMS = "SEARCH_TERMS";
    public static final String CONSUMER_CONTENT = "CONTENT";
    public static final String CONSUMER_USER = "USER";
    public static final String CONSUMER_SEARCH = "SEARCH";

    private String jobId;
    private String taskType;
    private Long sourceId;
    private String requestPayload;
    private String contextPayload;
    private long createdAt;
}
