package com.game.community.model.dto.user;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新用户资料请求
 */
@Data
public class UpdateUserInfoDTO {

    @NotNull(message = "资料版本号不能为空")
    private Integer version;

    @Size(min = 2, max = 32, message = "昵称长度必须在2到32个字符之间")
    private String username;

    @Size(max = 120, message = "个性签名不能超过120个字符")
    private String signature;

    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @Size(max = 64, message = "游戏账号长度不能超过64个字符")
    private String gameAccount;
}
