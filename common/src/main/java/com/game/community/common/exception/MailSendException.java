package com.game.community.common.exception;

/**
 * 邮件发送失败（由 GlobalExceptionHandler 转为统一 Result）
 */
public class MailSendException extends BusinessException {

    public MailSendException(int code, String message) {
        super(code, message);
    }
}
