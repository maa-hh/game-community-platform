package com.game.community.model.message;

import lombok.Data;

import java.io.Serializable;

/** AI 任务结果消息。业务服务根据 taskType 和 contextPayload 做幂等回写。 */
@Data
public class AiTaskResultMessage implements Serializable {

    private String jobId;
    private String taskType;
    private Long sourceId;
    private boolean success;
    private String resultPayload;
    private String contextPayload;
    private String errorMessage;
    private long completedAt;
}
