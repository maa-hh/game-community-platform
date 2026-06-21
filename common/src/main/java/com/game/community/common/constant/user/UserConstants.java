package com.game.community.common.constant.user;

/**
 * 用户模块常量
 */
public class UserConstants {

    public static final long CODE_EXPIRE = 300;

    public static final long ACCOUNT_ID_MIN = 10_000L;

    public static final long ACCOUNT_ID_MAX = 99_999_999L;

    public static final class UserType {
        public static final int NORMAL = 0;
        public static final int ADMIN = 1;

        private UserType() {
        }
    }

    public static final class UserStatus {
        public static final int NORMAL = 0;
        public static final int DISABLED = 1;

        private UserStatus() {
        }
    }

    public static final class AuditStatus {
        public static final int NONE = 0;
        public static final int AUDITING = 1;

        private AuditStatus() {
        }
    }

    public static final class AuditTaskType {
        public static final String PROFILE = "PROFILE";
        public static final String AVATAR = "AVATAR";

        private AuditTaskType() {
        }
    }

    public static final class AuditTaskStatus {
        public static final String PENDING = "PENDING";
        public static final String PROCESSING = "PROCESSING";
        public static final String PASSED = "PASSED";
        public static final String REJECTED = "REJECTED";
        public static final String FAILED = "FAILED";

        private AuditTaskStatus() {
        }
    }

    private UserConstants() {
    }
}
