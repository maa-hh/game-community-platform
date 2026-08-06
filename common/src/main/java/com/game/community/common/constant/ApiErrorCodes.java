package com.game.community.common.constant;

/**
 * 通用业务错误码（与 HTTP 语义对齐，前后端统一）
 */
public final class ApiErrorCodes {

    public static final int SUCCESS = 200;

    public static final int BAD_REQUEST = 400;

    public static final int UNAUTHORIZED = 401;

    public static final int FORBIDDEN = 403;

    public static final int NOT_FOUND = 404;

    public static final int CONFLICT = 409;

    public static final int TOO_MANY_REQUESTS = 429;

    public static final int INTERNAL_ERROR = 500;

    private ApiErrorCodes() {
    }
}
