package com.game.community.model.vo.user;

import lombok.Data;

/**
 * 简单用户信息
 */
@Data
public class UserSimpleVO {

    private Long id;

    private Long accountId;

    private String username;

    private String avatar;

    private String signature;

    private String gameAccount;
}
