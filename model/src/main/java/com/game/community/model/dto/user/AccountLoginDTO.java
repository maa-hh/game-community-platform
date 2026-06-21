package com.game.community.model.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 账号密码登录请求
 */
@Data
public class AccountLoginDTO {

    @NotNull(message = "账号ID不能为空")
    private Long accountId;

    @NotBlank(message = "密码不能为空")
    private String password;

    /**
     * 可选登录身份。为空时按账号 ID 登录，登录成功后由用户表 type 决定前端能力。
     */
    private Integer type;
}
