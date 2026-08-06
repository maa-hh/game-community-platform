package com.game.community.model.enums.user;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/**
 * 邮箱验证码业务类型（请求入参与内部逻辑统一使用枚举，序列化为同名字符串）
 */
public enum CodeBizType {

    /** 注册 */
    REGISTER,
    /** 找回密码 */
    RESET_PASSWORD,
    /** 改邮箱：向原邮箱发码 */
    CHANGE_EMAIL_OLD,
    /** 改邮箱：向新邮箱发码 */
    CHANGE_EMAIL_NEW,
    /** 注销账号 */
    CANCEL_ACCOUNT;

    @JsonValue
    public String getCode() {
        return name();
    }

    @JsonCreator
    public static CodeBizType from(String raw) {
        if (raw == null || raw.isBlank()) {
            return REGISTER;
        }
        try {
            return CodeBizType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return REGISTER;
        }
    }
}
