package com.game.community.model.dto.shop;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

@Data
public class CreateShopOrderDTO implements Serializable {

    @NotNull(message = "商品ID不能为空")
    private Long itemId;

    @Min(value = 1, message = "购买数量至少为1")
    @Max(value = 10, message = "单次最多购买10件")
    private Integer quantity = 1;

    @NotNull(message = "支付方式不能为空")
    private Integer payType;

    @NotBlank(message = "请求号不能为空")
    private String requestId;

    private Long userCouponId;
}
