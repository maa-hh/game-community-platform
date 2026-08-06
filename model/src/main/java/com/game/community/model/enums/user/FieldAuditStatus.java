package com.game.community.model.enums.user;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 资料字段审核状态（t_user_profile_audit.*_audit_status）
 */
public enum FieldAuditStatus {

    NONE(0),
    AUDITING(1),
    HUMAN_REVIEW(2);

    @EnumValue
    private final int code;

    FieldAuditStatus(int code) {
        this.code = code;
    }

    @JsonValue
    public int getCode() {
        return code;
    }

    @JsonCreator
    public static FieldAuditStatus from(Integer raw) {
        if (raw == null) {
            return NONE;
        }
        for (FieldAuditStatus status : values()) {
            if (status.code == raw) {
                return status;
            }
        }
        return NONE;
    }

    public boolean isBusy() {
        return this == AUDITING || this == HUMAN_REVIEW;
    }
}
