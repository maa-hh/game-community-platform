package com.game.community.notification.stream;

import com.game.community.common.constant.notification.NotificationConstants;
import com.game.community.model.message.NotificationEventMessage;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationAggregateTest {

    @Test
    void mergeMultipleLikesShouldBuildAggregatedEvent() {
        NotificationAggregate aggregate = NotificationAggregate.empty();

        aggregate.merge(buildLikeEvent(2L, "alice", 1L, 100L, null, null));
        aggregate.merge(buildLikeEvent(3L, "bob", 1L, 100L, null, null));

        NotificationEventMessage event = aggregate.toEvent();

        assertThat(event.getRecipientUserId()).isEqualTo(1L);
        assertThat(event.getArticleId()).isEqualTo(100L);
        assertThat(event.getEventType()).isEqualTo(NotificationConstants.EventType.ARTICLE_LIKE);
        assertThat(event.getPreviewText()).isEqualTo("bob 等 2 人赞了");
        assertThat(event.getActorUserId()).isEqualTo(3L);

        NotificationAggregatePayload payload = NotificationAggregatePayloadCodec.decode(event.getResultText());
        assertThat(payload).isNotNull();
        assertThat(payload.getActors()).hasSize(2);
        assertThat(payload.getTotal()).isEqualTo(2);
        assertThat(payload.getHasLike()).isTrue();
        assertThat(payload.getHasFavorite()).isFalse();
    }

    @Test
    void mergeLikeAndFavoriteShouldUseCombinedPreview() {
        NotificationAggregate aggregate = NotificationAggregate.empty();
        aggregate.merge(buildLikeEvent(2L, "alice", 1L, 100L, null, null));
        aggregate.merge(buildFavoriteEvent(3L, "bob", 1L, 100L));

        NotificationEventMessage event = aggregate.toEvent();

        assertThat(event.getPreviewText()).isEqualTo("bob 等 2 人赞和收藏了");
        assertThat(event.getEventType()).isEqualTo(NotificationConstants.EventType.ARTICLE_LIKE);
        NotificationAggregatePayload payload = NotificationAggregatePayloadCodec.decode(event.getResultText());
        assertThat(payload).isNotNull();
        assertThat(payload.getHasLike()).isTrue();
        assertThat(payload.getHasFavorite()).isTrue();
    }

    @Test
    void aggregateKeyShouldSeparateCommentTargets() {
        String articleKey = NotificationAggregateKey.build(
                buildLikeEvent(2L, "alice", 1L, 100L, null, null));
        String commentKey = NotificationAggregateKey.build(
                buildLikeEvent(2L, "alice", 1L, 100L, 10L, null));

        assertThat(articleKey).isNotEqualTo(commentKey);
    }

    private NotificationEventMessage buildLikeEvent(Long actorId, String actorName, Long recipientId,
                                                    Long articleId, Long commentId, Long replyId) {
        NotificationEventMessage event = new NotificationEventMessage();
        event.setEventType(NotificationConstants.EventType.ARTICLE_LIKE);
        event.setRecipientUserId(recipientId);
        event.setActorUserId(actorId);
        event.setActorUsername(actorName);
        event.setArticleId(articleId);
        event.setCommentId(commentId);
        event.setReplyId(replyId);
        event.setRouteType(NotificationConstants.RouteType.ARTICLE);
        event.setOccurredAt(LocalDateTime.now());
        return event;
    }

    private NotificationEventMessage buildFavoriteEvent(Long actorId, String actorName, Long recipientId,
                                                        Long articleId) {
        NotificationEventMessage event = buildLikeEvent(actorId, actorName, recipientId, articleId, null, null);
        event.setEventType(NotificationConstants.EventType.ARTICLE_FAVORITE);
        return event;
    }
}
