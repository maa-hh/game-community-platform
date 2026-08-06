package com.game.community.common.constant;

/**
 * 通用常量
 */
public class Constants {

    private static String env(String key, String defaultValue) {
        String value = System.getenv(key);
        if (value != null && !value.isBlank()) {
            return value.trim();
        }
        value = System.getProperty(key);
        if (value != null && !value.isBlank()) {
            return value.trim();
        }
        return defaultValue;
    }

    /** 生产环境务必设置 JWT_ACCESS_SECRET（≥32 字符随机串） */
    public static final String ACCESS_JWT_SECRET = env(
            "JWT_ACCESS_SECRET", "game-community-access-token-secret-key");

    /** 生产环境务必设置 JWT_REFRESH_SECRET（≥32 字符随机串） */
    public static final String REFRESH_JWT_SECRET = env(
            "JWT_REFRESH_SECRET", "game-community-refresh-token-secret-key");

    /** 与前端 game-community REFRESH_COOKIE_NAME 一致 */
    public static final String REFRESH_TOKEN_COOKIE_NAME = "game_community_refresh_token";

    public static final String REFRESH_TOKEN_COOKIE_PATH = "/";

    public static final long ACCESS_TOKEN_EXPIRE_TIME = 30 * 60L;

    public static final long REFRESH_TOKEN_EXPIRE_TIME = 7 * 24 * 60 * 60L;

    public static final long REFRESH_TOKEN_RENEW_WINDOW = 24 * 60 * 60L;

    private Constants() {
    }
}
