package com.game.community.model.vo.user;

import lombok.Data;

/**
 * 注册成功响应（仅创建账号，不含 token）
 */
@Data
public class RegisterVO {

    private String email;
}
