package com.game.community.model.enums.user;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/**
 * 资料字段审核类型（与 t_user_audit_task.task_type 对齐）
 */
public enum AuditFieldType {

    USERNAME,
    SIGNATURE,
    AVATAR;

    @JsonValue
    public String getCode() {
        return name();
    }

    public String label() {
        return switch (this) {
            case USERNAME -> "昵称";
            case SIGNATURE -> "个性签名";
            case AVATAR -> "头像";
        };
    }

    public String busyMessage() {
        return label() + "审核中，请稍后再改";
    }

    @JsonCreator
    public static AuditFieldType from(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("审核字段类型不能为空");
        }
        return AuditFieldType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    }
}
