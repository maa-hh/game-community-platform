package com.game.community.common.constant;

/**
 * 通用常量
 */
public class Constants {

    public static final String ACCESS_JWT_SECRET = "game-community-access-token-secret-key";

    public static final String REFRESH_JWT_SECRET = "game-community-refresh-token-secret-key";

    public static final String REFRESH_TOKEN_COOKIE_NAME = "refreshToken";

    public static final String REFRESH_TOKEN_COOKIE_PATH = "/";

    public static final long ACCESS_TOKEN_EXPIRE_TIME = 30 * 60L;

    public static final long REFRESH_TOKEN_EXPIRE_TIME = 7 * 24 * 60 * 60L;

    public static final long REFRESH_TOKEN_RENEW_WINDOW = 24 * 60 * 60L;

    private Constants() {
    }
}
