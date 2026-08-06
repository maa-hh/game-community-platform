package com.game.community.content.event;

import com.game.community.common.constant.KafkaTopicConstants;
import com.game.community.common.constant.notification.NotificationConstants;
import com.game.community.content.service.ContentOutboxService;
import com.game.community.model.message.NotificationEventMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ArticleNotificationEventProducer {

    private final ContentOutboxService contentOutboxService;

    public void publishArticleAuditRejected(Long recipientUserId, Long articleId, String reason) {
        NotificationEventMessage event = new NotificationEventMessage();
        event.setEventId(UUID.randomUUID().toString());
        event.setRecipientUserId(recipientUserId);
        event.setEventType(NotificationConstants.EventType.ARTICLE_AUDIT_REJECTED);
        event.setRouteType(NotificationConstants.RouteType.ARTICLE);
        event.setArticleId(articleId);
        event.setActorUserId(0L);
        event.setActorUsername("系统通知");
        event.setPreviewText("帖子审核未通过");
        event.setResultText(reason);
        event.setOccurredAt(LocalDateTime.now());
        contentOutboxService.enqueue(
                "notification:article-rejected:" + articleId + ":" + UUID.randomUUID(),
                "NOTIFICATION_EVENT", KafkaTopicConstants.NOTIFICATION_EVENT_TOPIC,
                String.valueOf(recipientUserId), event);
    }

    public void publishArticleAuditHumanReview(Long recipientUserId, Long articleId, String reason) {
        NotificationEventMessage event = new NotificationEventMessage();
        event.setEventId(UUID.randomUUID().toString());
        event.setRecipientUserId(recipientUserId);
        event.setEventType(NotificationConstants.EventType.ARTICLE_AUDIT_HUMAN_REVIEW);
        event.setRouteType(NotificationConstants.RouteType.ARTICLE);
        event.setArticleId(articleId);
        event.setActorUserId(0L);
        event.setActorUsername("系统通知");
        event.setPreviewText("帖子已进入人工审核");
        event.setResultText(reason);
        event.setOccurredAt(LocalDateTime.now());
        contentOutboxService.enqueue(
                "notification:article-human-review:" + articleId + ":" + UUID.randomUUID(),
                "NOTIFICATION_EVENT", KafkaTopicConstants.NOTIFICATION_EVENT_TOPIC,
                String.valueOf(recipientUserId), event);
    }
}
