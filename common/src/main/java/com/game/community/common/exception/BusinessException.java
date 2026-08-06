package com.game.community.common.exception;

/**
 * 业务异常（可携带业务码，默认 500）
 */
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(String message) {
        this(500, message);
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
