package com.game.community.common.constant.user;

/**
 * 用户模块 Redis 常量
 */
public class RedisConstants {

    public static final long RESERVE_COOLDOWN = 0;
    public static final long RESERVE_DAILY_LIMIT = 2;
    public static final long RESERVE_UNAVAILABLE = -1;

    public static final String CODE_PREFIX = "email:code:";

    public static final String SEND_CODE_COOLDOWN_PREFIX = "email:cooldown:";

    public static final String SEND_CODE_DAILY_PREFIX = "email:daily:";

    public static final String CODE_VERIFY_FAIL_PREFIX = "email:verify:fail:";

    public static final String CODE_VERIFY_LOCK_PREFIX = "email:verify:lock:";

    public static final String SESSION_PREFIX = "user:session:";

    /** 会话是否仍为活跃登录态（与 user:active-session 同步维护，供鉴权 MGET） */
    public static final String SESSION_ACTIVE_PREFIX = "user:session-active:";

    public static final String ACTIVE_SESSION_PREFIX = "user:active-session:";

    public static final String REGISTER_LOCK_PREFIX = "user:register:lock:";

    public static final String LOGIN_LOCK_PREFIX = "user:login:lock:";

    public static final String REFRESH_LOCK_PREFIX = "user:refresh:lock:";

    public static String codeKey(String bizType, String email) {
        return CODE_PREFIX + bizType + ":" + email;
    }

    private RedisConstants() {
    }
}
