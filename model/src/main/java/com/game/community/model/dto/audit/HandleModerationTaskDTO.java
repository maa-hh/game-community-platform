package com.game.community.model.dto.audit;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

@Data
public class HandleModerationTaskDTO implements Serializable {

    @NotBlank(message = "处理结果不能为空")
    private String handleAction;

    @NotBlank(message = "认领令牌不能为空")
    @Size(max = 64, message = "认领令牌不能超过64个字符")
    private String claimToken;

    @NotBlank(message = "处理请求标识不能为空")
    @Size(max = 64, message = "处理请求标识不能超过64个字符")
    private String requestId;

    @Size(max = 255, message = "处理说明不能超过255字")
    private String handleRemark;
}
