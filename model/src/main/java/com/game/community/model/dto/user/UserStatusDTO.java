package com.game.community.model.dto.user;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 用户状态请求
 */
@Data
public class UserStatusDTO {

    @NotNull(message = "状态不能为空")
    private Integer status;
}
