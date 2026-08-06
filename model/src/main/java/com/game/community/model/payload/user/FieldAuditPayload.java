package com.game.community.model.payload.user;

import com.game.community.model.enums.user.AuditFieldType;
import com.game.community.model.enums.user.UserStrings;
import lombok.Data;

/**
 * 字段审核任务负载
 */
@Data
public class FieldAuditPayload {

    private AuditFieldType field;

    private String content;

    private String oldAvatarUrl;

    private String pendingObjectName;

    public String getContent() {
        return UserStrings.orEmpty(content);
    }

    public String getOldAvatarUrl() {
        return UserStrings.orEmpty(oldAvatarUrl);
    }

    public String getPendingObjectName() {
        return UserStrings.orEmpty(pendingObjectName);
    }
}
