package com.game.community.model.payload.user;

import lombok.Data;

@Data
public class ProfileAuditPayload {

    private Integer userVersion;

    private String username;

    private String signature;

    private String phone;

    private String gameAccount;
}
