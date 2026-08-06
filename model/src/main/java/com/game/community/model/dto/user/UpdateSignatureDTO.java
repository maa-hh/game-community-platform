package com.game.community.model.dto.user;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改个性签名（空白/换行不计字数，由服务端按有效字符 ≤50 校验）
 */
@Data
public class UpdateSignatureDTO {

    @Size(max = 200, message = "个性签名过长")
    private String signature;
}
