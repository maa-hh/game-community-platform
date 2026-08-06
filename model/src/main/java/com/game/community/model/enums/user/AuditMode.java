package com.game.community.model.enums.user;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/**
 * 审核模式（MOCK / LLM）
 */
public enum AuditMode {

    MOCK,
    LLM;

    @EnumValue
    private final String code;

    AuditMode() {
        this.code = name();
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static AuditMode from(String raw) {
        if (raw == null || raw.isBlank()) {
            return MOCK;
        }
        return AuditMode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    }
}
