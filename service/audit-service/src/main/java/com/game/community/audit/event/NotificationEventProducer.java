package com.game.community.audit.event;

import com.game.community.common.constant.KafkaTopicConstants;
import com.game.community.common.constant.notification.NotificationConstants;
import com.game.community.model.message.NotificationEventMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventProducer {

    private final KafkaTemplate<String, NotificationEventMessage> kafkaTemplate;

    public void publishReportResult(Long recipientUserId, Integer routeType, Long articleId, Long commentId,
                                    Long replyId, Long targetUserId, Long reportId, String previewText, String resultText) {
        publish(buildEvent(recipientUserId, NotificationConstants.EventType.REPORT_RESULT, routeType,
                articleId, commentId, replyId, targetUserId, reportId, previewText, resultText));
    }

    public void publishPenaltyResult(Long recipientUserId, Integer routeType, Long articleId, Long commentId,
                                     Long replyId, Long targetUserId, Long reportId, String previewText, String resultText) {
        publish(buildEvent(recipientUserId, NotificationConstants.EventType.PENALTY_RESULT, routeType,
                articleId, commentId, replyId, targetUserId, reportId, previewText, resultText));
    }

    private NotificationEventMessage buildEvent(Long recipientUserId, Integer eventType, Integer routeType,
                                                Long articleId, Long commentId, Long replyId, Long targetUserId,
                                                Long reportId, String previewText, String resultText) {
        NotificationEventMessage event = new NotificationEventMessage();
        event.setEventType(eventType);
        event.setRecipientUserId(recipientUserId);
        event.setActorUserId(0L);
        event.setActorUsername("审核中心");
        event.setArticleId(articleId);
        event.setCommentId(commentId);
        event.setReplyId(replyId);
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
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    doPublish(event);
                }
            });
            return;
        }
        doPublish(event);
    }

    private void doPublish(NotificationEventMessage event) {
        kafkaTemplate.send(KafkaTopicConstants.NOTIFICATION_EVENT_TOPIC, String.valueOf(event.getRecipientUserId()), event)
                .whenComplete((result, error) -> {
                    if (error != null) {
                        log.warn("发送审核通知失败: recipientUserId={}, eventType={}, error={}",
                                event.getRecipientUserId(), event.getEventType(), error.getMessage());
                    }
                });
    }
}
