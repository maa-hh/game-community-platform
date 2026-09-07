package com.game.community.model.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

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

    /** Feed 收件箱记录 ID；仅 Feed 未读事件使用，用于消费端幂等。 */
    private Long feedItemId;

    private Long commentId;

    private Long replyId;

    private Long danmakuId;

    private String videoPublicId;

    /** 游戏评价通知定位信息。 */
    private Long gameAppId;

    private String gameReviewId;

    private String gameReviewReplyId;

    private Long reportId;

    private Long targetUserId;

    /** 跳转目标用户对外账号 ID */
    private Long targetAccountId;

    private Integer routeType;

    private String previewText;

    private String resultText;

    /** 个人主页数据失效域；仅 PROFILE_DATA_INVALIDATED 事件使用。 */
    private List<String> invalidationDomains;

    private LocalDateTime occurredAt;
}
