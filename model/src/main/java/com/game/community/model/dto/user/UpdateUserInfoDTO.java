package com.game.community.model.dto.user;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 更新非审核资料（当前仅 Steam 账号）
 */
@Data
public class UpdateUserInfoDTO {

    @NotNull(message = "资料版本不能为空")
    private Integer version;

    @Size(max = 64, message = "Steam账号长度不能超过64个字符")
    private String steamAccount;
}
