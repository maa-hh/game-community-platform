package com.game.community.notification.service;

import com.game.community.common.constant.KafkaTopicConstants;
import com.game.community.model.message.NotificationEventMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventConsumer {

    private final NotificationService notificationService;

    @KafkaListener(topics = KafkaTopicConstants.NOTIFICATION_EVENT_TOPIC, groupId = "notification-service-group")
    public void consume(NotificationEventMessage event) {
        if (event == null || event.getRecipientUserId() == null || event.getEventType() == null) {
            log.warn("忽略无效通知事件: {}", event);
            return;
        }
        notificationService.consumeNotificationEvent(event);
    }
}
