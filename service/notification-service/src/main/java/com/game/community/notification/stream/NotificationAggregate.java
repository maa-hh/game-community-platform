package com.game.community.notification.stream;

import com.game.community.common.constant.notification.NotificationConstants;
import com.game.community.model.message.NotificationEventMessage;
import com.game.community.model.vo.notification.NotificationActorVO;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Data
@NoArgsConstructor
public class NotificationAggregate implements Serializable {

    private static final int MAX_ACTOR_SNAPSHOTS = 10;

    private Long recipientUserId;

    private Integer eventType;

    private Integer routeType;

    private Long articleId;

    private Long commentId;

    private Long replyId;

    private Long targetUserId;

    private Long primaryActorUserId;

    private LocalDateTime occurredAt;

    private String latestActorUsername;

    private String latestActorAvatar;

    private Long latestActorAccountId;

    private int actorCount;

    private Map<Long, NotificationActorVO> actorMap = new LinkedHashMap<>();

    private Set<Long> actorIds = new HashSet<>();

    private boolean hasLike;

    private boolean hasFavorite;

    public static NotificationAggregate empty() {
        return new NotificationAggregate();
    }

    public NotificationAggregate merge(NotificationEventMessage event) {
        if (event == null) {
            return this;
        }
        if (recipientUserId == null) {
            recipientUserId = event.getRecipientUserId();
            eventType = event.getEventType();
            routeType = event.getRouteType();
            articleId = event.getArticleId();
            commentId = event.getCommentId();
            replyId = event.getReplyId();
            targetUserId = event.getTargetUserId();
            occurredAt = event.getOccurredAt();
        }
        if (event.getActorUserId() != null && event.getActorUserId() > 0) {
            NotificationActorVO actor = new NotificationActorVO();
            actor.setAccountId(event.getActorAccountId());
            actor.setUsername(event.getActorUsername());
            actor.setAvatar(event.getActorAvatar());
            actor.setAction(actionOf(event.getEventType()));
            primaryActorUserId = event.getActorUserId();
            latestActorUsername = event.getActorUsername();
            latestActorAvatar = event.getActorAvatar();
            latestActorAccountId = event.getActorAccountId();
            boolean newActor = actorIds.add(event.getActorUserId());
            if (actorMap.size() < MAX_ACTOR_SNAPSHOTS || actorMap.containsKey(event.getActorUserId())) {
                actorMap.put(event.getActorUserId(), actor);
            }
            if (newActor) {
                actorCount++;
            }
        }
        if (event.getEventType() != null
                && event.getEventType() == NotificationConstants.EventType.ARTICLE_FAVORITE) {
            hasFavorite = true;
        } else {
            hasLike = true;
        }
        if (event.getOccurredAt() != null) {
            occurredAt = event.getOccurredAt();
        }
        return this;
    }

    public NotificationEventMessage toEvent() {
        NotificationEventMessage event = new NotificationEventMessage();
        event.setRecipientUserId(recipientUserId);
        event.setEventType(eventType);
        event.setRouteType(routeType);
        event.setArticleId(articleId);
        event.setCommentId(commentId);
        event.setReplyId(replyId);
        event.setTargetUserId(targetUserId);
        event.setOccurredAt(occurredAt == null ? LocalDateTime.now() : occurredAt);

        List<NotificationActorVO> actors = new ArrayList<>(actorMap.values());
        int total = Math.max(actorCount, actors.size());
        NotificationActorVO primary = actors.isEmpty() ? null : actors.get(actors.size() - 1);
        if (primary != null) {
            event.setActorUserId(primaryActorUserId);
            event.setActorAccountId(latestActorAccountId);
            event.setActorUsername(latestActorUsername);
            event.setActorAvatar(latestActorAvatar);
        }
        event.setPreviewText(buildPreview(actors, total));
        event.setResultText(NotificationAggregatePayloadCodec.encode(actors, total));
        event.setEventId("aggregate:" + NotificationAggregateKey.build(event) + ":" +
                (occurredAt == null ? "0" : occurredAt.toEpochSecond(java.time.ZoneOffset.UTC)));
        if (hasFavorite && hasLike) {
            event.setEventType(NotificationConstants.EventType.ARTICLE_LIKE);
        } else if (hasFavorite) {
            event.setEventType(NotificationConstants.EventType.ARTICLE_FAVORITE);
        }
        return event;
    }

    private String buildPreview(List<NotificationActorVO> actors, int total) {
        if (actors.isEmpty()) {
            return "收到新的互动";
        }
        String latest = actors.get(actors.size() - 1).getUsername();
        if (total <= 1) {
            return latest + actionText();
        }
        return latest + " 等 " + total + " 人" + actionText();
    }

    private String actionText() {
        if (hasFavorite && hasLike) {
            return "赞和收藏了";
        }
        if (hasFavorite) {
            return "收藏了";
        }
        return "赞了";
    }

    private String actionOf(Integer type) {
        if (type != null && type == NotificationConstants.EventType.ARTICLE_FAVORITE) {
            return "favorite";
        }
        return "like";
    }
}
