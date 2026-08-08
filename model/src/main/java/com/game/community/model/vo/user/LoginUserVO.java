package com.game.community.model.vo.user;

import lombok.Data;

/**
 * 登录成功时附带的当前用户摘要
 */
@Data
public class LoginUserVO {

    /** 对外账号 ID；t_user.id 仅在服务内部使用。 */
    private Long accountId;

    private String username;

    private String avatar;

    private String email;
}
