package com.game.community.notification.stream;

import com.game.community.model.message.NotificationEventMessage;

public final class NotificationAggregateKey {

    private NotificationAggregateKey() {
    }

    public static String build(NotificationEventMessage event) {
        return event.getRecipientUserId()
                + ":"
                + nullSafe(event.getArticleId())
                + ":"
                + nullSafe(event.getCommentId())
                + ":"
                + nullSafe(event.getReplyId());
    }

    private static String nullSafe(Long value) {
        return value == null ? "0" : String.valueOf(value);
    }
}
