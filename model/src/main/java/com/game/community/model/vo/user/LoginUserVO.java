package com.game.community.model.vo.user;

import lombok.Data;

/**
 * 登录成功时附带的当前用户摘要
 */
@Data
public class LoginUserVO {

    private Long userId;

    private Long accountId;

    private String username;

    private String avatar;

    private String email;
}
