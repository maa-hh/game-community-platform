package com.game.community.common.constant.social;

import java.util.Set;

/**
 * 社交领域跨模块共享的状态、类型和动作常量。
 */
public final class SocialConstants {

    /** 文章分享渠道，接口只接受这些稳定的协议值。 */
    public static final Set<String> SHARE_CHANNELS = Set.of("link", "repost", "external");

    /** 统计表允许更新的字段白名单，避免把动态字段名直接交给 SQL。 */
    public static final Set<String> ARTICLE_STAT_COLUMNS = Set.of(
            "like_count", "comment_count", "comment_like_count", "reply_count",
            "reply_like_count", "view_count", "favorite_count", "share_count");
    public static final Set<String> COMMENT_COUNTER_COLUMNS = Set.of("like_count", "reply_count");
    public static final Set<String> REPLY_COUNTER_COLUMNS = Set.of("like_count");
    public static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";
    public static final String FEED_PUBLISH_PATH = "/feign/social/feed/publish";
    public static final String FEED_ARTICLE_DELETE_PATH_PREFIX = "/feign/social/feed/article/";
    public static final String BLACK_RELATION_CACHE_KEY_PREFIX = "social:black:relation:";
    public static final long BLACK_RELATION_CACHE_SECONDS = 30;

    /** 事件类型由社交服务生产、由 outbox 发布器消费。 */
    public static final class EventType {
        public static final String ARTICLE_BEHAVIOR = "ARTICLE_BEHAVIOR";
        public static final String NOTIFICATION = "NOTIFICATION";
        public static final String REPORT_AUDIT = "REPORT_AUDIT";

        private EventType() {
        }
    }

    public static final class GameReviewStatus {
        public static final int NORMAL = 1;

        private GameReviewStatus() {
        }
    }

    public static class CommentStatus {
        public static final int NORMAL = 1;
        public static final int HIDDEN = 2;
        public static final int DELETED = 3;

        private CommentStatus() {
        }
    }

    public static class ReplyStatus {
        public static final int NORMAL = 1;
        public static final int HIDDEN = 2;
        public static final int DELETED = 3;

        private ReplyStatus() {
        }
    }

    public static class ReportTargetType {
        public static final int ARTICLE = 1;
        public static final int COMMENT = 2;
        public static final int REPLY = 3;
        public static final int USER = 4;
        public static final int DANMAKU = 5;
        /** 站点问题反馈，复用举报链路但不关联具体内容目标。 */
        public static final int FEEDBACK = 6;

        private ReportTargetType() {
        }
    }

    public static class ReportStatus {
        public static final int PENDING = 0;
        public static final int ACCEPTED = 1;
        public static final int REJECTED = 2;
        public static final int PROCESSING = 3;

        private ReportStatus() {
        }
    }

    public static class FeedSourceType {
        public static final int PUBLISH_PUSH = 1;
        public static final int FOLLOW_COMPENSATION = 2;
        public static final int SELF = 3;

        private FeedSourceType() {
        }
    }

    private SocialConstants() {
    }
}
