package com.game.community.user.event;

import com.game.community.common.constant.notification.NotificationConstants;
import com.game.community.model.enums.user.AuditFieldType;
import com.game.community.model.message.NotificationEventMessage;
import com.game.community.user.service.UserNotificationOutboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 用户资料审核结果推送（Kafka → notification-service → SSE）
 */
@Component
@RequiredArgsConstructor
public class ProfileAuditNotificationProducer {

    private final UserNotificationOutboxService outboxService;

    public void publishPassed(Long userId, AuditFieldType field, Integer score, String reason) {
        publish(userId, NotificationConstants.EventType.PROFILE_AUDIT_PASSED, field,
                field.label() + "审核通过",
                formatResult(score, reason));
    }

    public void publishRejected(Long userId, AuditFieldType field, Integer score, String reason) {
        publish(userId, NotificationConstants.EventType.PROFILE_AUDIT_REJECTED, field,
                field.label() + "审核未通过",
                formatResult(score, reason));
    }

    public void publishHumanReview(Long userId, AuditFieldType field, Integer score, String reason) {
        publish(userId, NotificationConstants.EventType.PROFILE_AUDIT_HUMAN_REVIEW, field,
                field.label() + "进入人工审核",
                formatResult(score, reason));
    }

    private void publish(Long userId, int eventType, AuditFieldType field, String previewText, String resultText) {
        NotificationEventMessage event = new NotificationEventMessage();
        event.setEventId(UUID.randomUUID().toString());
        event.setEventType(eventType);
        event.setRecipientUserId(userId);
        event.setActorUserId(0L);
        event.setActorUsername("资料审核");
        event.setTargetUserId(userId);
        event.setRouteType(NotificationConstants.RouteType.USER);
        event.setPreviewText(previewText);
        event.setResultText("[" + field.getCode() + "] " + resultText);
        event.setOccurredAt(LocalDateTime.now());
        send(event);
    }

    private void send(NotificationEventMessage event) {
        if (event.getRecipientUserId() == null || event.getEventType() == null) {
            return;
        }
        outboxService.enqueue(event);
    }

    private String formatResult(Integer score, String reason) {
        StringBuilder builder = new StringBuilder();
        if (score != null) {
            builder.append("得分").append(score);
        }
        if (StringUtils.hasText(reason)) {
            if (!builder.isEmpty()) {
                builder.append("：");
            }
            builder.append(reason);
        }
        return builder.isEmpty() ? "" : builder.toString();
    }
}
