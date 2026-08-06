package com.game.community.model.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 邮箱验证码注册请求
 */
@Data
public class RegisterDTO {

    @NotBlank(message = "请输入邮箱")
    @Pattern(regexp = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$", message = "请输入正确的邮箱格式")
    private String email;

    @NotBlank(message = "请设置密码")
    @Size(min = 6, max = 10, message = "密码为 6-10 位数字、字母或特殊字符")
    @Pattern(regexp = "^[a-zA-Z0-9!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?`~]{6,10}$",
            message = "密码为 6-10 位数字、字母或特殊字符")
    private String password;

    @NotBlank(message = "请输入验证码")
    @Pattern(regexp = "^\\d{6}$", message = "验证码为 6 位数字")
    private String code;
}
