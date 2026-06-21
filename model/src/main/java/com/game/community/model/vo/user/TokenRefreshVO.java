package com.game.community.model.vo.user;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

/**
 * 刷新令牌响应
 */
@Data
public class TokenRefreshVO {

    private String accessToken;

    private Long accessTokenExpireIn;

    @JsonIgnore
    private String refreshToken;
}
