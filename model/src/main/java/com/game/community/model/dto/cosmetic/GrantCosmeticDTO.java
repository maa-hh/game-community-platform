package com.game.community.model.dto.cosmetic;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

@Data
public class GrantCosmeticDTO implements Serializable {

    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @NotBlank(message = "装扮编码不能为空")
    private String cosmeticCode;

    @Min(value = 1, message = "数量至少为1")
    private Integer quantity = 1;

    @NotBlank(message = "订单号不能为空")
    private String orderNo;

    private String sourceType = "SHOP";
}
