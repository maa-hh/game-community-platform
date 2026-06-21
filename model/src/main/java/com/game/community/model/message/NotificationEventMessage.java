package com.game.community.model.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationEventMessage implements Serializable {

    private Integer eventType;

    private Long recipientUserId;

    private Long actorUserId;

    private String actorUsername;

    private String actorAvatar;

    private Long articleId;

    private Long commentId;

    private Long replyId;

    private Long reportId;

    private Long targetUserId;

    private Integer routeType;

    private String previewText;

    private String resultText;

    private LocalDateTime occurredAt;
}
