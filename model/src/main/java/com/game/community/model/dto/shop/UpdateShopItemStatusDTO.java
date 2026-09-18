package com.game.community.model.dto.shop;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/** 商品上下架请求。 */
@Data
public class UpdateShopItemStatusDTO implements Serializable {

    @NotNull(message = "商品状态不能为空")
    @Min(value = 0, message = "商品状态不合法")
    @Max(value = 1, message = "商品状态不合法")
    private Integer status;
}
