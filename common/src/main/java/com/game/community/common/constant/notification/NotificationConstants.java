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
        /** 视频弹幕互动 */
        public static final int DANMAKU_COMMENT = 18;
        /** 个人主页数据失效，不落通知表，只通过 SSE 通知前端。 */
        public static final int PROFILE_DATA_INVALIDATED = 19;
        /** 游戏评价回复及评价互动。 */
        public static final int GAME_REVIEW_REPLY = 20;
        public static final int GAME_REVIEW_LIKE = 21;
        public static final int GAME_REVIEW_REPLY_LIKE = 22;

        private EventType() {
        }

        public static boolean isSupported(Integer eventType) {
            return eventType != null && (eventType >= ARTICLE_LIKE && eventType <= ARTICLE_AUDIT_HUMAN_REVIEW
                    || (eventType >= DANMAKU_COMMENT && eventType <= GAME_REVIEW_REPLY_LIKE));
        }
    }

    public static final class RouteType {
        public static final int NONE = 0;
        public static final int ARTICLE = 1;
        public static final int COMMENT = 2;
        public static final int REPLY = 3;
        public static final int USER = 4;
        public static final int DANMAKU = 5;
        public static final int GAME_REVIEW = 6;

        private RouteType() {
        }
    }

    public static final class ReadStatus {
        public static final int UNREAD = 0;
        public static final int READ = 1;

        private ReadStatus() {
        }
    }

    public static final class Outbox {
        public static final int DELIVERY_FAILED = 4;
        public static final int INITIAL_RETRY_COUNT = 0;
        public static final int MAX_ERROR_LENGTH = 1000;

        private Outbox() {
        }
    }

    public static final class SseEventType {
        public static final String NOTIFICATION_CREATED = "notification_created";
        public static final String NOTIFICATION_SUMMARY = "notification_summary";
        public static final String FEED_UNREAD = "feed_unread";
        public static final String HEARTBEAT = "heartbeat";
        public static final String PROFILE_INVALIDATED = "profile_invalidated";

        private SseEventType() {
        }
    }

    /** 跨 notification-service 实例的 Redis 发布订阅协议。 */
    public static final class RedisChannel {
        public static final String SSE_BROADCAST = "notification:sse:broadcast";

        private RedisChannel() {
        }
    }

    /** 通知表与跨服务消息协议的字段上限。 */
    public static final class FieldLimit {
        public static final int EVENT_ID = 96;
        public static final int ACTOR_USERNAME = 64;
        public static final int ACTOR_AVATAR = 512;
        public static final int VIDEO_PUBLIC_ID = 128;
        public static final int GAME_REVIEW_ID = 64;
        public static final int PREVIEW_TEXT = 255;

        private FieldLimit() {
        }
    }

    /** 通知分页与断线补发的统一边界。 */
    public static final class Pagination {
        public static final long DEFAULT_SIZE = 20L;
        public static final long MAX_SIZE = 100L;
        public static final int MAX_REPLAY_SIZE = 100;

        private Pagination() {
        }
    }

    public static final class ProfileDataDomain {
        public static final String BASE = "profile.base";
        public static final String STATS = "profile.stats";
        public static final String FOLLOWING = "profile.following";
        public static final String FEED = "profile.feed";
        public static final String FOLLOWERS = "profile.followers";
        public static final String POSTS = "profile.posts";
        public static final String HISTORY = "profile.history";
        public static final String LIKED = "profile.liked";
        public static final String RECEIVED = "profile.received";
        public static final String FAVORITES = "profile.favorites";
        public static final String COMMENTS = "profile.comments";

        private ProfileDataDomain() {
        }
    }
}
