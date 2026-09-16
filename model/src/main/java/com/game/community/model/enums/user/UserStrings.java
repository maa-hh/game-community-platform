package com.game.community.model.enums.user;

/**
 * 用户模块字符串字段默认值（避免向库表写入 null）
 */
public final class UserStrings {

    public static final String EMPTY = "";

    /** 新注册用户的默认昵称。 */
    public static final String DEFAULT_NICKNAME = "新玩家";

    /** 注销完成后替换原邮箱的前缀和保留域名。 */
    public static final String CANCELLED_EMAIL_PREFIX = "cancelled_";
    public static final String INVALID_EMAIL_DOMAIN = "@invalid.local";

    /** 审核恢复拒绝日志中的来源标识。 */
    public static final String AUDIT_RECOVERY_SOURCE = "periodic-recovery";

    /** 登录锁定时间的展示格式。 */
    public static final String LOCK_TIME_FORMAT_PATTERN = "yyyy-MM-dd HH:mm";

    private UserStrings() {
    }

    public static String orEmpty(String value) {
        return value == null ? EMPTY : value;
    }
}
