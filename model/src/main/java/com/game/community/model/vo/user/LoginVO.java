package com.game.community.model.vo.user;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 登录响应
 */
@Data
public class LoginVO {

    private String accessToken;

    @JsonProperty("accessExpiresIn")
    private Long accessExpiresIn;

    private LoginUserVO user;

    /** 仅服务端写 Cookie 使用，不输出到 JSON */
    @JsonIgnore
    private String refreshToken;
}
