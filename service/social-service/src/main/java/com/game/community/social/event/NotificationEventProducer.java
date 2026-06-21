package com.game.community.social.event;

import com.game.community.common.constant.KafkaTopicConstants;
import com.game.community.common.constant.notification.NotificationConstants;
import com.game.community.common.constant.social.SocialConstants;
import com.game.community.model.message.NotificationEventMessage;
import com.game.community.model.vo.user.UserVO;
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

    public void publishArticleLike(Long recipientUserId, UserVO actor, Long articleId) {
        publish(buildEvent(recipientUserId, actor,
                NotificationConstants.EventType.ARTICLE_LIKE,
                NotificationConstants.RouteType.ARTICLE,
                articleId, null, null, null, null,
                safeUsername(actor) + " 点赞了你的帖子",
                null));
    }

    public void publishArticleComment(Long recipientUserId, UserVO actor, Long articleId, Long commentId, String content) {
        publish(buildEvent(recipientUserId, actor,
                NotificationConstants.EventType.ARTICLE_COMMENT,
                NotificationConstants.RouteType.COMMENT,
                articleId, commentId, null, null, null,
                safeUsername(actor) + " 评论了你的帖子",
                trimText(content)));
    }

    public void publishCommentReply(Long recipientUserId, UserVO actor, Long articleId, Long commentId, Long replyId, String content) {
        publish(buildEvent(recipientUserId, actor,
                NotificationConstants.EventType.COMMENT_REPLY,
                NotificationConstants.RouteType.REPLY,
                articleId, commentId, replyId, null, null,
                safeUsername(actor) + " 回复了你",
                trimText(content)));
    }

    public void publishCommentLike(Long recipientUserId, UserVO actor, Long articleId, Long commentId) {
        publish(buildEvent(recipientUserId, actor,
                NotificationConstants.EventType.COMMENT_LIKE,
                NotificationConstants.RouteType.COMMENT,
                articleId, commentId, null, null, null,
                safeUsername(actor) + " 点赞了你的评论",
                null));
    }

    public void publishReplyLike(Long recipientUserId, UserVO actor, Long articleId, Long commentId, Long replyId) {
        publish(buildEvent(recipientUserId, actor,
                NotificationConstants.EventType.REPLY_LIKE,
                NotificationConstants.RouteType.REPLY,
                articleId, commentId, replyId, null, null,
                safeUsername(actor) + " 点赞了你的回复",
                null));
    }

    public void publishFollow(Long recipientUserId, UserVO actor) {
        publish(buildEvent(recipientUserId, actor,
                NotificationConstants.EventType.FOLLOW,
                NotificationConstants.RouteType.USER,
                null, null, null, null, actor == null ? null : actor.getId(),
                safeUsername(actor) + " 关注了你",
                null));
    }

    public void publishReportSubmitted(Long recipientUserId, Long targetType, Long articleId, Long commentId,
                                       Long replyId, Long targetUserId, String reason) {
        NotificationEventMessage event = systemEvent(
                recipientUserId,
                NotificationConstants.EventType.REPORT_SUBMITTED,
                routeTypeForTargetType(targetType),
                articleId, commentId, replyId, null, targetUserId,
                "举报已提交，管理员会尽快处理",
                trimText(reason)
        );
        publish(event);
    }

    public void publishFeedUnread(Long recipientUserId, LocalDateTime occurredAt) {
        NotificationEventMessage event = systemEvent(
                recipientUserId,
                NotificationConstants.EventType.FEED_UNREAD,
                NotificationConstants.RouteType.NONE,
                null, null, null, null, null,
                "",
                null
        );
        event.setOccurredAt(occurredAt == null ? LocalDateTime.now() : occurredAt);
        publish(event);
    }

    private NotificationEventMessage buildEvent(Long recipientUserId, UserVO actor, Integer eventType, Integer routeType,
                                                Long articleId, Long commentId, Long replyId, Long reportId, Long targetUserId,
                                                String previewText, String resultText) {
        NotificationEventMessage event = new NotificationEventMessage();
        event.setEventType(eventType);
        event.setRecipientUserId(recipientUserId);
        event.setActorUserId(actor == null ? null : actor.getId());
        event.setActorUsername(safeUsername(actor));
        event.setActorAvatar(actor == null ? null : actor.getAvatar());
        event.setArticleId(articleId);
        event.setCommentId(commentId);
        event.setReplyId(replyId);
        event.setReportId(reportId);
        event.setTargetUserId(targetUserId);
        event.setRouteType(routeType);
        event.setPreviewText(previewText);
        event.setResultText(resultText);
        event.setOccurredAt(LocalDateTime.now());
        return event;
    }

    private NotificationEventMessage systemEvent(Long recipientUserId, Integer eventType, Integer routeType,
                                                 Long articleId, Long commentId, Long replyId, Long reportId, Long targetUserId,
                                                 String previewText, String resultText) {
        NotificationEventMessage event = buildEvent(recipientUserId, null, eventType, routeType,
                articleId, commentId, replyId, reportId, targetUserId, previewText, resultText);
        event.setActorUserId(0L);
        event.setActorUsername("系统通知");
        return event;
    }

    private Integer routeTypeForTargetType(Long targetType) {
        if (targetType == null) {
            return NotificationConstants.RouteType.NONE;
        }
        return switch (targetType.intValue()) {
            case SocialConstants.ReportTargetType.ARTICLE -> NotificationConstants.RouteType.ARTICLE;
            case SocialConstants.ReportTargetType.COMMENT -> NotificationConstants.RouteType.COMMENT;
            case SocialConstants.ReportTargetType.REPLY -> NotificationConstants.RouteType.REPLY;
            case SocialConstants.ReportTargetType.USER -> NotificationConstants.RouteType.USER;
            default -> NotificationConstants.RouteType.NONE;
        };
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
                        log.warn("发送通知事件失败: recipientUserId={}, eventType={}, error={}",
                                event.getRecipientUserId(), event.getEventType(), error.getMessage());
                    }
                });
    }

    private String safeUsername(UserVO actor) {
        if (actor == null) {
            return "玩家";
        }
        if (actor.getUsername() != null && !actor.getUsername().isBlank()) {
            return actor.getUsername();
        }
        return actor.getId() == null ? "玩家" : "玩家" + actor.getId();
    }

    private String trimText(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        if (trimmed.length() <= 120) {
            return trimmed;
        }
        return trimmed.substring(0, 117) + "...";
    }
}
