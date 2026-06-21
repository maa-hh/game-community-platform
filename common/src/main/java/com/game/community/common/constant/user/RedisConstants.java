package com.game.community.common.constant.user;

/**
 * 用户模块 Redis 常量
 */
public class RedisConstants {

    public static final String CODE_PREFIX = "sms:code:";

    public static final String SESSION_PREFIX = "user:session:";

    public static final String ACTIVE_SESSION_PREFIX = "user:active-session:";

    public static final String REGISTER_LOCK_PREFIX = "user:register:lock:";

    public static final String LOGIN_LOCK_PREFIX = "user:login:lock:";

    public static final String REFRESH_LOCK_PREFIX = "user:refresh:lock:";

    private RedisConstants() {
    }
}
