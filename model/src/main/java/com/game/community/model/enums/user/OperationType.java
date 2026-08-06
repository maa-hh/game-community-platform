package com.game.community.model.enums.user;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/**
 * 用户操作日志类型
 */
public enum OperationType {

    LOGIN,
    LOGOUT,
    CHANGE_PASSWORD,
    RESET_PASSWORD,
    BAN,
    UNBAN,
    CANCEL_APPLY,
    CANCEL_REVOKE,
    CANCEL_COMPLETE;

    @EnumValue
    private final String code;

    OperationType() {
        this.code = name();
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static OperationType from(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("操作类型不能为空");
        }
        return OperationType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    }
}
