package com.game.community.model.vo.user;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户账户状态VO（供Feign跨服务查询）
 */
@Data
public class UserAccountVO {

    private Long userId;

    /** 0正常 1封禁 2注销中 3已注销 */
    private Integer status;

    /** 0普通用户 1管理员 */
    private Integer type;

    private LocalDateTime banUntil;

    private String banReason;
}
