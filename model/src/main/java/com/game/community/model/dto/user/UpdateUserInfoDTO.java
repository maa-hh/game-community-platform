package com.game.community.model.dto.user;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新非审核资料（当前仅 Steam 账号）
 */
@Data
public class UpdateUserInfoDTO {

    @Size(max = 64, message = "Steam账号长度不能超过64个字符")
    private String steamAccount;
}
