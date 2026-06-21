package com.game.community.model.vo.user;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

/**
 * 登录响应
 */
@Data
public class LoginVO {

    private String accessToken;

    private Long accessTokenExpireIn;

    private Long userId;

    private Long accountId;

    private String username;

    private String avatar;

    private Integer type;

    private String gameAccount;

    private Integer auditStatus;

    @JsonIgnore
    private String refreshToken;
}
