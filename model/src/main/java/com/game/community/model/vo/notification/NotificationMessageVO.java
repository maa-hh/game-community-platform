package com.game.community.model.vo.notification;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class NotificationMessageVO implements Serializable {

    private Long id;

    private Integer eventType;

    private Long actorUserId;

    private String actorUsername;

    private String actorAvatar;

    private Long articleId;

    private Long commentId;

    private Long replyId;

    private Long reportId;

    private Long targetUserId;

    private String previewText;

    private String resultText;

    private Integer routeType;

    private Integer readStatus;

    private LocalDateTime readTime;

    private LocalDateTime createTime;
}
