package com.game.community.common.constant.user;

/**
 * 用户模块数值/时间类配置常量。
 * <p>
 * 业务类型请使用 {@code com.game.community.model.enums.user} 包下枚举。
 */
public class UserConstants {

    public static final int INITIAL_VERSION = 0;
    public static final int INITIAL_FAIL_COUNT = 0;
    public static final int NOT_DELETED = 0;
    public static final int DELETED = 1;
    public static final int ACCOUNT_POOL_AVAILABLE = 0;
    public static final int ACCOUNT_POOL_RESERVED = 1;

    public static final int MAX_BATCH_QUERY_SIZE = 100;
    public static final int FIRST_PAGE = 1;
    public static final int USER_SEARCH_DEFAULT_PAGE_SIZE = 10;
    public static final int USER_SEARCH_MAX_PAGE_SIZE = 20;

    public static final int USERNAME_MIN_LENGTH = 2;
    public static final int USERNAME_MAX_LENGTH = 20;
    public static final int SIGNATURE_MAX_LENGTH = 50;

    public static final long MIN_POSITIVE_SECONDS = 1;
    public static final int SECONDS_PER_MINUTE = 60;
    public static final int CODE_LENGTH = 6;
    public static final int CODE_RANDOM_BOUND = 1_000_000;

    public static final int REGISTER_LOCK_SECONDS = 10;
    public static final int SESSION_LOCK_SECONDS = 5;
    public static final int ACCOUNT_ID_RESERVE_MAX_RETRIES = 3;
    public static final String DEFAULT_NICKNAME = "新玩家";
    public static final String CANCELLED_EMAIL_PREFIX = "cancelled_";
    public static final String INVALID_EMAIL_DOMAIN = "@invalid.local";

    /** 验证码有效期默认值（秒）；实际以 email.code.expire-seconds 配置为准 */
    public static final long CODE_EXPIRE = 300;

    /** 验证码发送冷却期（秒），与前端 SEND_COUNTDOWN=60 一致 */
    public static final long SEND_CODE_COOLDOWN = 60;

    /** 每日验证码发送上限（注册/找回密码各自独立计数） */
    public static final int SEND_CODE_DAILY_LIMIT = 10;

    /** 号池初始位数 */
    public static final int ACCOUNT_ID_INITIAL_DIGITS = 5;

    public static final long ACCOUNT_ID_MIN = 10_000L;

    public static final long ACCOUNT_ID_MAX = 99_999_999L;

    /** 号池单次扩容条数 */
    public static final int ACCOUNT_ID_EXPAND_BATCH = 1000;

    /** 注销冷静期（天） */
    public static final int CANCEL_COOLDOWN_DAYS = 7;

    /** 登录失败锁定阈值（连续错误次数） */
    public static final int LOGIN_FAIL_LOCK_THRESHOLD = 10;

    /** 登录锁定时长（分钟），24 小时 */
    public static final int LOGIN_LOCK_DURATION_MINUTES = 24 * 60;

    /** 验证码验证失败锁定阈值（次数） */
    public static final int CODE_VERIFY_FAIL_THRESHOLD = 5;

    /** 验证码验证失败锁定时长（分钟） */
    public static final int CODE_VERIFY_LOCK_DURATION_MINUTES = 30;

    /** 验证码验证失败计数过期时间（秒） */
    public static final long CODE_VERIFY_FAIL_EXPIRE = 1800;

    /** 审核任务超时时间（分钟） */
    public static final int AUDIT_TASK_STALE_MINUTES = 30;

    /** 服务启动恢复审核任务时每批最多读入的任务数 */
    public static final int AUDIT_RECOVERY_BATCH_SIZE = 100;

    /** 合规分阈值：0-3 拒绝，4-6 人工，7-10 通过 */
    public static final class AuditScore {
        public static final int MIN = 0;
        public static final int MAX = 10;
        public static final int REJECT_MAX = 3;
        public static final int HUMAN_REVIEW_MAX = 6;
        public static final int REJECT_DEFAULT = 1;
        public static final int PASS_DEFAULT = 9;
        public static final int HUMAN_REVIEW_PASS = 8;
        public static final int ERROR_MAX_LENGTH = 255;

        private AuditScore() {
        }
    }

    private UserConstants() {
    }
}
