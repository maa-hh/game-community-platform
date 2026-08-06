package com.game.community.model.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改密码请求（与全站 PasswordValidator 规则一致：6-10）
 */
@Data
public class ChangePasswordDTO {

    @NotBlank(message = "原密码不能为空")
    private String oldPassword;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 10, message = "新密码长度为6到10位")
    @Pattern(
            regexp = "^[a-zA-Z0-9!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?`~]+$",
            message = "新密码仅支持数字、字母或常见特殊字符"
    )
    private String newPassword;

    /** 确认密码（须与新密码一致） */
    @NotBlank(message = "请确认新密码")
    private String confirmPassword;
}
