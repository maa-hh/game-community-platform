package com.game.community.audit.event;

import com.game.community.common.constant.notification.NotificationConstants;
import com.game.community.audit.service.AuditNotificationOutboxService;
import com.game.community.model.message.NotificationEventMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class NotificationEventProducer {

    private final AuditNotificationOutboxService outboxService;

    public void publishReportResult(String taskKey, Long recipientUserId, Integer routeType, Long articleId, Long commentId,
                                    Long replyId, Long danmakuId, String videoPublicId, Long targetUserId,
                                    Long reportId, String previewText, String resultText) {
        publish(buildEvent(eventId(taskKey, NotificationConstants.EventType.REPORT_RESULT, recipientUserId),
                recipientUserId, NotificationConstants.EventType.REPORT_RESULT, routeType,
                articleId, commentId, replyId, danmakuId, videoPublicId, targetUserId, reportId,
                previewText, resultText));
    }

    public void publishPenaltyResult(String taskKey, Long recipientUserId, Integer routeType, Long articleId, Long commentId,
                                     Long replyId, Long danmakuId, String videoPublicId, Long targetUserId,
                                     Long reportId, String previewText, String resultText) {
        publish(buildEvent(eventId(taskKey, NotificationConstants.EventType.PENALTY_RESULT, recipientUserId),
                recipientUserId, NotificationConstants.EventType.PENALTY_RESULT, routeType,
                articleId, commentId, replyId, danmakuId, videoPublicId, targetUserId, reportId,
                previewText, resultText));
    }

    public void publishArticleAuditPassed(String taskKey, Long recipientUserId, Long articleId,
                                          String previewText, String resultText) {
        publish(buildEvent(eventId(taskKey, NotificationConstants.EventType.ARTICLE_AUDIT_PASSED, recipientUserId),
                recipientUserId, NotificationConstants.EventType.ARTICLE_AUDIT_PASSED,
                NotificationConstants.RouteType.ARTICLE, articleId, null, null, null, null, null, null,
                previewText, resultText));
    }

    private NotificationEventMessage buildEvent(String eventId, Long recipientUserId, Integer eventType, Integer routeType,
                                                Long articleId, Long commentId, Long replyId, Long danmakuId,
                                                String videoPublicId, Long targetUserId, Long reportId,
                                                String previewText, String resultText) {
        NotificationEventMessage event = new NotificationEventMessage();
        event.setEventId(eventId);
        event.setEventType(eventType);
        event.setRecipientUserId(recipientUserId);
        event.setActorUserId(0L);
        event.setActorUsername("审核中心");
        event.setArticleId(articleId);
        event.setCommentId(commentId);
        event.setReplyId(replyId);
        event.setDanmakuId(danmakuId);
        event.setVideoPublicId(videoPublicId);
        event.setTargetUserId(targetUserId);
        event.setReportId(reportId);
        event.setRouteType(routeType == null ? NotificationConstants.RouteType.NONE : routeType);
        event.setPreviewText(previewText);
        event.setResultText(resultText);
        event.setOccurredAt(LocalDateTime.now());
        return event;
    }

    /** 为同一审核工单生成稳定事件号，兼容 Kafka 至少一次投递和 Outbox 重试。 */
    private String eventId(String taskKey, Integer eventType, Long recipientUserId) {
        String source = String.valueOf(taskKey) + ':' + eventType + ':' + recipientUserId;
        return UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private void publish(NotificationEventMessage event) {
        if (event == null || event.getRecipientUserId() == null || event.getEventType() == null) {
            return;
        }
        outboxService.enqueue(event);
    }
}
