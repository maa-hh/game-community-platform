package com.game.community.audit.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.community.common.constant.KafkaTopicConstants;
import com.game.community.audit.mapper.AuditNotificationOutboxMapper;
import com.game.community.model.entity.audit.AuditNotificationOutbox;
import com.game.community.model.message.NotificationEventMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuditNotificationOutboxPublisher {

    private final AuditNotificationOutboxMapper mapper;
    private final KafkaTemplate<String, NotificationEventMessage> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${audit.notification-outbox.batch-size:100}")
    private int batchSize;

    @Value("${audit.notification-outbox.lock-timeout-ms:600000}")
    private long lockTimeoutMs;

    @Scheduled(fixedDelayString = "${audit.notification-outbox.poll-interval-ms:500}")
    public void publishPending() {
        mapper.releaseStale(LocalDateTime.now().minusNanos(
                Math.max(60_000L, lockTimeoutMs) * 1_000_000L));
        List<AuditNotificationOutbox> events = mapper.selectPending(Math.max(1, Math.min(batchSize, 500)));
        for (AuditNotificationOutbox event : events) {
            String token = UUID.randomUUID().toString().replace("-", "");
            if (mapper.claim(event.getId(), token) == 1) {
                publish(event, token);
            }
        }
    }

    private void publish(AuditNotificationOutbox outbox, String token) {
        try {
            NotificationEventMessage event = objectMapper.readValue(outbox.getPayload(), NotificationEventMessage.class);
            kafkaTemplate.send(KafkaTopicConstants.NOTIFICATION_EVENT_TOPIC,
                            String.valueOf(event.getRecipientUserId()), event)
                    .whenComplete((ignored, error) -> {
                        if (error == null) {
                            mapper.markSent(outbox.getId(), token);
                        } else {
                            mapper.markFailed(outbox.getId(), token, String.valueOf(error.getMessage()));
                        }
                    });
        } catch (Exception e) {
            mapper.markFailed(outbox.getId(), token, e.getMessage());
            log.warn("审核通知 Outbox 投递失败: id={}", outbox.getId(), e);
        }
    }
}
