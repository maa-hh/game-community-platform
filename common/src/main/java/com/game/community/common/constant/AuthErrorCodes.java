package com.game.community.common.constant;

/**
 * 认证相关业务码（与前端 service/config.ts 对齐）
 */
public final class AuthErrorCodes {

    /** access 失效 */
    public static final int ACCESS_EXPIRED = 40101;

    /** refresh 失效 */
    public static final int REFRESH_EXPIRED = 40102;

    private AuthErrorCodes() {
    }
}
