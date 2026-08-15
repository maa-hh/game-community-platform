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

    /** 事件唯一标识。Kafka 至少一次投递时用于通知落库幂等。 */
    private String eventId;

    private Integer eventType;

    private Long recipientUserId;

    private Long actorUserId;

    /** 触发人对外账号 ID（事件链路携带，VO 层直接透出） */
    private Long actorAccountId;

    private String actorUsername;

    private String actorAvatar;

    private Long articleId;

    private Long commentId;

    private Long replyId;

    private Long danmakuId;

    private String videoPublicId;

    private Long reportId;

    private Long targetUserId;

    /** 跳转目标用户对外账号 ID */
    private Long targetAccountId;

    private Integer routeType;

    private String previewText;

    private String resultText;

    private LocalDateTime occurredAt;
}
