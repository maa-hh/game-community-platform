package com.game.community.model.enums.user;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/**
 * 审核任务状态（t_user_audit_task.status）
 */
public enum AuditTaskStatus {

    PENDING,
    PROCESSING,
    PASSED,
    REJECTED,
    HUMAN_REVIEW,
    FAILED;

    @EnumValue
    private final String code;

    AuditTaskStatus() {
        this.code = name();
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static AuditTaskStatus from(String raw) {
        if (raw == null || raw.isBlank()) {
            return PENDING;
        }
        return AuditTaskStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    }
}
