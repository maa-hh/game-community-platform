package com.game.community.model.dto.shop;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

@Data
public class ExchangeShopItemDTO implements Serializable {

    @NotNull(message = "商品ID不能为空")
    private Long itemId;

    @Min(value = 1, message = "购买数量至少为1")
    @Max(value = 10, message = "单次最多购买10件")
    private Integer quantity = 1;

    @NotBlank(message = "请求号不能为空")
    @Size(max = 80, message = "请求号不能超过80个字符")
    private String requestId;
}
