package com.game.community.notification.service;

import com.game.community.common.constant.KafkaTopicConstants;
import com.game.community.common.constant.notification.NotificationConstants;
import com.game.community.model.message.NotificationEventMessage;
import com.game.community.model.message.DanmakuEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventConsumer {

    private final NotificationService notificationService;

    @KafkaListener(topics = KafkaTopicConstants.NOTIFICATION_EVENT_READY_TOPIC, groupId = "notification-service-group")
    public void consume(NotificationEventMessage event) {
        if (event == null || event.getRecipientUserId() == null || event.getEventType() == null) {
            log.warn("忽略无效通知事件: {}", event);
            return;
        }
        if (!NotificationConstants.EventType.isSupported(event.getEventType())) {
            throw new IllegalArgumentException("未知通知事件类型: " + event.getEventType());
        }
        notificationService.consumeNotificationEvent(event);
    }

    /** 弹幕可靠事件使用独立消费组，避免影响通知主链路；事件 ID 仍由通知落库层统一幂等。 */
    @KafkaListener(
            topics = KafkaTopicConstants.DANMAKU_TOPIC,
            groupId = "notification-service-danmaku-group",
            properties = "spring.json.value.default.type:com.game.community.model.message.DanmakuEvent")
    public void consumeDanmaku(DanmakuEvent event) {
        if (event == null || event.getEventId() == null) {
            log.warn("忽略无效弹幕通知事件: {}", event);
            return;
        }
        notificationService.consumeDanmakuEvent(event);
    }
}
