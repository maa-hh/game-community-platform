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

    /** 账号类型：0普通用户，1管理员。 */
    private Integer type;
}
