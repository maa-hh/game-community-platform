package com.game.community.model.dto.user;

import com.game.community.model.enums.user.CodeBizType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 发送邮箱验证码请求
 */
@Data
public class SendCodeDTO {

    @NotBlank(message = "请输入邮箱")
    @Pattern(regexp = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$", message = "请输入正确的邮箱格式")
    private String email;

    /** 业务类型，缺省为 {@link CodeBizType#REGISTER} */
    private CodeBizType bizType;
}
