package com.game.community.model.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改昵称（空白不计字数，由服务端按有效字符 2~20 校验）
 */
@Data
public class UpdateUsernameDTO {

    @NotNull(message = "资料版本不能为空")
    private Integer version;

    @NotBlank(message = "昵称不能为空")
    @Size(max = 60, message = "昵称过长")
    private String username;
}
