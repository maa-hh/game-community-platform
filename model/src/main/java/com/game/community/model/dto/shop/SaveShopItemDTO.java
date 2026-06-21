package com.game.community.model.dto.shop;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class SaveShopItemDTO implements Serializable {

    private Long id;

    @NotBlank(message = "商品名称不能为空")
    private String name;

    private String description;

    @NotNull(message = "价格不能为空")
    @Min(value = 0, message = "价格不能小于0")
    private Integer price;

    @NotNull(message = "商品类型不能为空")
    private Integer productType;

    private Integer stock;

    private String icon;

    private Integer status;

    private String businessCode;

    private Long businessId;

    private Integer quantity;

    private Integer limitCount;

    private LocalDateTime beginTime;

    private LocalDateTime endTime;
}
