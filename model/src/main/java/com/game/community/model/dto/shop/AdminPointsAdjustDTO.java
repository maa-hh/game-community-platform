package com.game.community.model.dto.shop;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 管理员积分调整请求。requestId 由调用方生成，用于重试幂等和流水追踪。
 */
@Data
public class AdminPointsAdjustDTO implements Serializable {

    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @NotNull(message = "积分数量不能为空")
    @Positive(message = "积分数量必须大于0")
    private Long amount;

    @NotBlank(message = "请求号不能为空")
    @Size(max = 80, message = "请求号不能超过80个字符")
    private String requestId;

    @Size(max = 255, message = "备注不能超过255个字符")
    private String remark;
}
