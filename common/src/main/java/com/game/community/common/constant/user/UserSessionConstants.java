package com.game.community.common.constant.user;

/**
 * 用户会话与 JWT 的协议常量。
 */
public final class UserSessionConstants {

    public static final String TOKEN_TYPE_ACCESS = "access";
    public static final String TOKEN_TYPE_REFRESH = "refresh";
    public static final String CLAIM_ACCOUNT_ID = "accountId";
    public static final String CLAIM_ACCOUNT_TYPE = "type";
    public static final String CLAIM_STEAM_ACCOUNT = "steamAccount";
    public static final String CLAIM_SESSION_ID = "sessionId";
    public static final String CLAIM_TOKEN_TYPE = "tokenType";
    public static final String TOKEN_DIGEST_ALGORITHM = "SHA-256";

    private UserSessionConstants() {
    }
}
