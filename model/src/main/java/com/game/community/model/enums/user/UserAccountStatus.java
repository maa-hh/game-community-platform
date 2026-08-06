package com.game.community.model.enums.user;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 用户账户状态（t_user_account.status）
 */
public enum UserAccountStatus {

    NORMAL(0),
    BANNED(1),
    CANCELLING(2),
    CANCELLED(3);

    @EnumValue
    private final int code;

    UserAccountStatus(int code) {
        this.code = code;
    }

    @JsonValue
    public int getCode() {
        return code;
    }

    @JsonCreator
    public static UserAccountStatus from(Integer raw) {
        if (raw == null) {
            return NORMAL;
        }
        for (UserAccountStatus status : values()) {
            if (status.code == raw) {
                return status;
            }
        }
        return NORMAL;
    }
}
