package com.game.community.model.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 申请注销（需原邮箱验证码）
 */
@Data
public class CancelAccountDTO {

    @NotBlank(message = "请输入验证码")
    @Pattern(regexp = "^\\d{6}$", message = "验证码为6位数字")
    private String code;
}
