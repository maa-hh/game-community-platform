package com.game.community.common.constant.audit;

public final class ModerationConstants {

    /** 工单状态 */
    public static final class TaskStatus {
        public static final int PENDING = 0;
        public static final int PROCESSING = 1;
        public static final int COMPLETED = 2;

        private TaskStatus() {
        }
    }

    /** 工单类型 */
    public static final class TaskType {
        public static final String REPORT = "REPORT";
        public static final String ARTICLE_AUDIT = "ARTICLE_AUDIT";
        public static final String PROFILE_AUDIT = "PROFILE_AUDIT";

        private TaskType() {
        }
    }

    /** 处理结果 */
    public static final class HandleAction {
        public static final String NO_VIOLATION = "NO_VIOLATION";
        public static final String OFFLINE_ARTICLE = "OFFLINE_ARTICLE";
        public static final String HIDE_COMMENT = "HIDE_COMMENT";
        public static final String HIDE_REPLY = "HIDE_REPLY";
        public static final String HIDE_DANMAKU = "HIDE_DANMAKU";
        public static final String BAN_USER = "BAN_USER";
        public static final String AUDIT_APPROVE = "AUDIT_APPROVE";
        public static final String AUDIT_REJECT = "AUDIT_REJECT";
        public static final String PROFILE_APPROVE = "PROFILE_APPROVE";
        public static final String PROFILE_REJECT = "PROFILE_REJECT";

        private HandleAction() {
        }
    }

    /** 超时自动通过天数 */
    public static final int AUTO_PASS_DAYS = 3;

    /** 管理员认领租约分钟数。 */
    public static final int CLAIM_LEASE_MINUTES = 15;

    /** 系统自动处理使用的内部处理人标识。 */
    public static final long SYSTEM_HANDLER_ID = 0L;

    /** 审核目标状态快照 */
    public static final class TargetStatusSnapshot {
        public static final String ARTICLE_PENDING = "ARTICLE_PENDING";
        public static final String PROFILE_HUMAN_REVIEW = "PROFILE_HUMAN_REVIEW";

        private TargetStatusSnapshot() {
        }
    }

    private ModerationConstants() {
    }
}
