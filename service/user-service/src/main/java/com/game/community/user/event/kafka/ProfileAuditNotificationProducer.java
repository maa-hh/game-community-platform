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

    /** 执行 publishPassed 对应的业务处理。 */
    public void publishPassed(Long userId, AuditFieldType field, Integer score, String reason) {
        publish(userId, NotificationConstants.EventType.PROFILE_AUDIT_PASSED, field,
                field.label() + "审核通过",
                formatResult(score, reason));
    }

    /** 执行 publishRejected 对应的业务处理。 */
    public void publishRejected(Long userId, AuditFieldType field, Integer score, String reason) {
        publish(userId, NotificationConstants.EventType.PROFILE_AUDIT_REJECTED, field,
                field.label() + "审核未通过",
                formatResult(score, reason));
    }

    /** 执行 publishHumanReview 对应的业务处理。 */
    public void publishHumanReview(Long userId, AuditFieldType field, Integer score, String reason) {
        publish(userId, NotificationConstants.EventType.PROFILE_AUDIT_HUMAN_REVIEW, field,
                field.label() + "进入人工审核",
                formatResult(score, reason));
    }

    /** 执行 publish 对应的业务处理。 */
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

    /** 执行 send 对应的业务处理。 */
    private void send(NotificationEventMessage event) {
        if (event.getRecipientUserId() == null || event.getEventType() == null) {
            return;
        }
        try {
            // Kafka 负责正常投递和消费者侧重试；回调失败只落失败表，不在 user-service 增加扫描线程。
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

    /** 执行 recordFailure 对应的业务处理。 */
    private void recordFailure(NotificationEventMessage event, Throwable error) {
        try {
            // eventId 唯一约束保证回调和同步异常重复记录时仍保持幂等。
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

    /** 执行 formatResult 对应的业务处理。 */
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
