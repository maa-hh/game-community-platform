package com.game.community.model.vo.user;

import lombok.Data;

/**
 * 他人主页公开资料（按 accountId 查询）
 */
@Data
public class UserPublicVO {

    private Long accountId;

    private String username;

    private String avatar;

    private String signature;

    private String steamAccount;

    private Integer followCount;

    private Integer fansCount;
}
