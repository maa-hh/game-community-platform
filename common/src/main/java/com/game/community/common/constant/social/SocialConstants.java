package com.game.community.common.constant.social;

/**
 * 社交服务状态与类型常量。
 */
public class SocialConstants {

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
