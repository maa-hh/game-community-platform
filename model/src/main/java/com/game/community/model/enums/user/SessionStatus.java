package com.game.community.model.enums.user;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/**
 * Redis 会话状态
 */
public enum SessionStatus {

    ONLINE;

    @JsonValue
    public String getCode() {
        return name();
    }

    @JsonCreator
    public static SessionStatus from(String raw) {
        if (raw == null || raw.isBlank()) {
            return ONLINE;
        }
        return SessionStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    }
}
