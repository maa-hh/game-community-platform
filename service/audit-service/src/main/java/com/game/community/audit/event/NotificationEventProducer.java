package com.game.community.audit.event;

import com.game.community.common.constant.notification.NotificationConstants;
import com.game.community.audit.service.AuditNotificationOutboxService;
import com.game.community.model.message.NotificationEventMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class NotificationEventProducer {

    private final AuditNotificationOutboxService outboxService;

    public void publishReportResult(Long recipientUserId, Integer routeType, Long articleId, Long commentId,
                                    Long replyId, Long danmakuId, String videoPublicId, Long targetUserId,
                                    Long reportId, String previewText, String resultText) {
        publish(buildEvent(recipientUserId, NotificationConstants.EventType.REPORT_RESULT, routeType,
                articleId, commentId, replyId, danmakuId, videoPublicId, targetUserId, reportId,
                previewText, resultText));
    }

    public void publishPenaltyResult(Long recipientUserId, Integer routeType, Long articleId, Long commentId,
                                     Long replyId, Long danmakuId, String videoPublicId, Long targetUserId,
                                     Long reportId, String previewText, String resultText) {
        publish(buildEvent(recipientUserId, NotificationConstants.EventType.PENALTY_RESULT, routeType,
                articleId, commentId, replyId, danmakuId, videoPublicId, targetUserId, reportId,
                previewText, resultText));
    }

    public void publishArticleAuditPassed(Long recipientUserId, Long articleId, String previewText, String resultText) {
        publish(buildEvent(recipientUserId, NotificationConstants.EventType.ARTICLE_AUDIT_PASSED,
                NotificationConstants.RouteType.ARTICLE, articleId, null, null, null, null, null, null,
                previewText, resultText));
    }

    private NotificationEventMessage buildEvent(Long recipientUserId, Integer eventType, Integer routeType,
                                                Long articleId, Long commentId, Long replyId, Long danmakuId,
                                                String videoPublicId, Long targetUserId, Long reportId,
                                                String previewText, String resultText) {
        NotificationEventMessage event = new NotificationEventMessage();
        event.setEventId(UUID.randomUUID().toString());
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

    private void publish(NotificationEventMessage event) {
        if (event == null || event.getRecipientUserId() == null || event.getEventType() == null) {
            return;
        }
        outboxService.enqueue(event);
    }
}
