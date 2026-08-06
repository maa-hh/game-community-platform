package com.game.community.model.vo.audit;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class ModerationTaskVO implements Serializable {

    private Long id;

    private String taskType;

    private Long sourceId;

    private Integer targetType;

    private Long targetId;

    private String targetPublicId;

    private Long subjectUserId;

    private Long reporterId;

    private String subjectUserName;

    private String reporterName;

    private String reason;

    private String summary;

    private String extraPayload;

    private Integer status;

    private String handleAction;

    private Long handlerId;

    private String handlerName;

    private String handleRemark;

    private LocalDateTime claimTime;

    private LocalDateTime handleTime;

    private LocalDateTime createTime;
}
