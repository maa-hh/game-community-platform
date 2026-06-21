package com.game.community.common.constant.notification;

public final class NotificationConstants {

    private NotificationConstants() {
    }

    public static final class EventType {
        public static final int ARTICLE_LIKE = 1;
        public static final int ARTICLE_COMMENT = 2;
        public static final int COMMENT_REPLY = 3;
        public static final int COMMENT_LIKE = 4;
        public static final int REPLY_LIKE = 5;
        public static final int FOLLOW = 6;
        public static final int REPORT_SUBMITTED = 7;
        public static final int REPORT_RESULT = 8;
        public static final int PENALTY_RESULT = 9;
        public static final int FEED_UNREAD = 10;

        private EventType() {
        }
    }

    public static final class RouteType {
        public static final int NONE = 0;
        public static final int ARTICLE = 1;
        public static final int COMMENT = 2;
        public static final int REPLY = 3;
        public static final int USER = 4;

        private RouteType() {
        }
    }

    public static final class ReadStatus {
        public static final int UNREAD = 0;
        public static final int READ = 1;

        private ReadStatus() {
        }
    }

    public static final class SseEventType {
        public static final String NOTIFICATION_CREATED = "notification_created";
        public static final String NOTIFICATION_SUMMARY = "notification_summary";
        public static final String FEED_UNREAD = "feed_unread";
        public static final String HEARTBEAT = "heartbeat";

        private SseEventType() {
        }
    }
}
