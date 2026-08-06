package com.game.community.model.enums.user;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/**
 * 注册来源
 */
public enum RegisterSource {

    EMAIL,
    STEAM;

    @EnumValue
    private final String code;

    RegisterSource() {
        this.code = name();
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static RegisterSource from(String raw) {
        if (raw == null || raw.isBlank()) {
            return EMAIL;
        }
        return RegisterSource.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    }
}
