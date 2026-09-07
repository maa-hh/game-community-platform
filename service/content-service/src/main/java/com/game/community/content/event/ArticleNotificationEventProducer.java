package com.game.community.content.event;

import com.game.community.common.constant.KafkaTopicConstants;
import com.game.community.common.constant.notification.NotificationConstants;
import com.game.community.content.service.ContentOutboxService;
import com.game.community.model.message.NotificationEventMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
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

    public void publishProfileInvalidation(Long recipientUserId, String... domains) {
        if (recipientUserId == null || domains == null || domains.length == 0) {
            return;
        }
        List<String> normalized = Arrays.stream(domains)
                .filter(domain -> domain != null && !domain.isBlank())
                .distinct()
                .toList();
        if (normalized.isEmpty()) {
            return;
        }
        NotificationEventMessage event = new NotificationEventMessage();
        event.setEventId(UUID.randomUUID().toString());
        event.setRecipientUserId(recipientUserId);
        event.setEventType(NotificationConstants.EventType.PROFILE_DATA_INVALIDATED);
        event.setRouteType(NotificationConstants.RouteType.NONE);
        event.setActorUserId(0L);
        event.setActorUsername("系统通知");
        event.setInvalidationDomains(normalized);
        event.setOccurredAt(LocalDateTime.now());
        contentOutboxService.enqueue(
                "notification:profile-invalidated:" + recipientUserId + ":" + UUID.randomUUID(),
                "NOTIFICATION_EVENT", KafkaTopicConstants.NOTIFICATION_EVENT_TOPIC,
                String.valueOf(recipientUserId), event);
    }
}
