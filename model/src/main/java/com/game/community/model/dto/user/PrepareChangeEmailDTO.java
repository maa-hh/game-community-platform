package com.game.community.model.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 改邮箱第二步：校验原邮箱验证码后，向新邮箱发码
 */
@Data
public class PrepareChangeEmailDTO {

    @NotBlank(message = "请输入原邮箱验证码")
    @Pattern(regexp = "^\\d{6}$", message = "验证码为6位数字")
    private String oldCode;

    @NotBlank(message = "请输入新邮箱")
    @Pattern(regexp = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$", message = "请输入正确的邮箱格式")
    private String newEmail;
}
