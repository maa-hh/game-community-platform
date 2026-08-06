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
        /** 资料字段审核通过 */
        public static final int PROFILE_AUDIT_PASSED = 11;
        /** 资料字段审核拒绝 */
        public static final int PROFILE_AUDIT_REJECTED = 12;
        /** 资料字段进入人工复核 */
        public static final int PROFILE_AUDIT_HUMAN_REVIEW = 13;
        /** 帖子被收藏 */
        public static final int ARTICLE_FAVORITE = 14;
        /** 帖子审核不通过 */
        public static final int ARTICLE_AUDIT_REJECTED = 15;
        /** 帖子人工审核通过 */
        public static final int ARTICLE_AUDIT_PASSED = 16;
        /** 帖子进入人工审核 */
        public static final int ARTICLE_AUDIT_HUMAN_REVIEW = 17;

        private EventType() {
        }

        public static boolean isSupported(Integer eventType) {
            return eventType != null && eventType >= ARTICLE_LIKE && eventType <= ARTICLE_AUDIT_HUMAN_REVIEW;
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
