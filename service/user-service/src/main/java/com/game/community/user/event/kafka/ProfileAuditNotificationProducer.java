package com.game.community.user.event.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.community.common.constant.KafkaTopicConstants;
import com.game.community.common.constant.notification.NotificationConstants;
import com.game.community.model.entity.user.UserNotificationOutbox;
import com.game.community.model.enums.user.AuditFieldType;
import com.game.community.model.message.NotificationEventMessage;
import com.game.community.user.mapper.UserNotificationOutboxMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 用户资料审核结果推送（Kafka → notification-service → SSE）
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProfileAuditNotificationProducer {

    private final KafkaTemplate<String, NotificationEventMessage> kafkaTemplate;
    private final UserNotificationOutboxMapper failureMapper;
    private final ObjectMapper objectMapper;

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
        try {
            kafkaTemplate.send(KafkaTopicConstants.NOTIFICATION_EVENT_TOPIC,
                            String.valueOf(event.getRecipientUserId()), event)
                    .whenComplete((ignored, error) -> {
                        if (error != null) {
                            recordFailure(event, error);
                        }
                    });
        } catch (Exception e) {
            recordFailure(event, e);
        }
    }

    private void recordFailure(NotificationEventMessage event, Throwable error) {
        try {
            UserNotificationOutbox failure = new UserNotificationOutbox();
            failure.setEventKey(event.getEventId());
            failure.setPayload(objectMapper.writeValueAsString(event));
            failure.setStatus(NotificationConstants.Outbox.DELIVERY_FAILED);
            failure.setRetryCount(NotificationConstants.Outbox.INITIAL_RETRY_COUNT);
            String message = String.valueOf(error.getMessage());
            failure.setLastError(message.length() > NotificationConstants.Outbox.MAX_ERROR_LENGTH
                    ? message.substring(0, NotificationConstants.Outbox.MAX_ERROR_LENGTH) : message);
            failure.setCreateTime(LocalDateTime.now());
            failure.setUpdateTime(LocalDateTime.now());
            failureMapper.insert(failure);
        } catch (DuplicateKeyException e) {
            log.debug("通知失败记录已存在: eventId={}", event.getEventId());
        } catch (JsonProcessingException e) {
            log.error("通知失败记录序列化失败: eventId={}", event.getEventId(), e);
        } catch (Exception e) {
            log.error("通知失败记录落库失败: eventId={}", event.getEventId(), e);
        }
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
