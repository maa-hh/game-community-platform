package com.game.community.model.message;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class ModerationTaskMessage implements Serializable {

    private String taskType;

    private Long sourceId;

    private Integer targetType;

    private Long targetId;

    private Long subjectUserId;

    private Long reporterId;

    private String reason;

    private String summary;

    private String extraPayload;

    private String targetStatusSnapshot;

    private LocalDateTime targetUpdatedAt;

    private LocalDateTime eventTime;
}
