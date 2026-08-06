package com.game.community.social.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.community.model.entity.social.SocialOutboxEvent;
import com.game.community.model.message.ArticleBehaviorMessage;
import com.game.community.model.message.NotificationEventMessage;
import com.game.community.model.message.ReportAuditMessage;
import com.game.community.social.common.SocialOutboxEventTypes;
import com.game.community.social.mapper.SocialOutboxMapper;
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
public class SocialOutboxPublisher {

    private final SocialOutboxMapper socialOutboxMapper;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${social.outbox.batch-size:100}")
    private int batchSize;

    @Value("${social.outbox.stale-lock-minutes:2}")
    private int staleLockMinutes;

    @Scheduled(fixedDelayString = "${social.outbox.poll-interval-ms:500}")
    public void publishPending() {
        socialOutboxMapper.releaseStale(LocalDateTime.now().minusMinutes(staleLockMinutes));
        List<SocialOutboxEvent> events = socialOutboxMapper.selectPending(Math.max(1, Math.min(batchSize, 500)));
        for (SocialOutboxEvent event : events) {
            String token = UUID.randomUUID().toString().replace("-", "");
            if (socialOutboxMapper.claim(event.getId(), token) == 1) {
                publish(event, token);
            }
        }
    }

    private void publish(SocialOutboxEvent event, String token) {
        try {
            Object payload = deserialize(event);
            if (event.getTopic() == null || event.getTopic().isBlank()) {
                throw new IllegalArgumentException("社交 Outbox Kafka topic 为空");
            }
            kafkaTemplate.send(event.getTopic(), event.getMessageKey(), payload).whenComplete((ignored, error) -> {
                if (error == null) {
                    socialOutboxMapper.markSent(event.getId(), token);
                } else {
                    markFailed(event, token, error);
                }
            });
        } catch (Exception e) {
            markFailed(event, token, e);
        }
    }

    private Object deserialize(SocialOutboxEvent event) throws Exception {
        return switch (event.getEventType()) {
            case SocialOutboxEventTypes.ARTICLE_BEHAVIOR ->
                    objectMapper.readValue(event.getPayload(), ArticleBehaviorMessage.class);
            case SocialOutboxEventTypes.NOTIFICATION ->
                    objectMapper.readValue(event.getPayload(), NotificationEventMessage.class);
            case SocialOutboxEventTypes.REPORT_AUDIT ->
                    objectMapper.readValue(event.getPayload(), ReportAuditMessage.class);
            default -> throw new IllegalArgumentException("未知社交 Outbox 事件类型: " + event.getEventType());
        };
    }

    private void markFailed(SocialOutboxEvent event, String token, Throwable error) {
        String message = error == null ? "未知错误" : String.valueOf(error.getMessage());
        socialOutboxMapper.markFailed(event.getId(), token, message);
        log.warn("社交 Outbox 投递失败: id={}, type={}, error={}", event.getId(), event.getEventType(), message);
    }
}
