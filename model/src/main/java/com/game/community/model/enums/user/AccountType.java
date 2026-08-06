package com.game.community.model.enums.user;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 账号类型
 */
public enum AccountType {

    NORMAL(0),
    ADMIN(1);

    @EnumValue
    private final int code;

    AccountType(int code) {
        this.code = code;
    }

    @JsonValue
    public int getCode() {
        return code;
    }

    @JsonCreator
    public static AccountType from(Integer raw) {
        if (raw == null) {
            return NORMAL;
        }
        for (AccountType type : values()) {
            if (type.code == raw) {
                return type;
            }
        }
        return NORMAL;
    }
}
