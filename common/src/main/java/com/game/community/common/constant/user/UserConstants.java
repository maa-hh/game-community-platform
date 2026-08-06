package com.game.community.common.constant.user;

/**
 * 用户模块数值/时间类配置常量。
 * <p>
 * 业务类型请使用 {@code com.game.community.model.enums.user} 包下枚举。
 */
public class UserConstants {

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

    /** 合规分阈值：0-3 拒绝，4-6 人工，7-10 通过 */
    public static final class AuditScore {
        public static final int MIN = 0;
        public static final int MAX = 10;
        public static final int REJECT_MAX = 3;
        public static final int HUMAN_REVIEW_MAX = 6;

        private AuditScore() {
        }
    }

    private UserConstants() {
    }
}
