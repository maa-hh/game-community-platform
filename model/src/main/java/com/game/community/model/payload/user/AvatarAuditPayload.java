package com.game.community.model.payload.user;

import lombok.Data;

@Data
public class AvatarAuditPayload {

    private Integer userVersion;

    private String oldAvatarUrl;

    private String pendingObjectName;
}
