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

    @NotBlank(message = "装扮编码不能为空")
    private String cosmeticCode;

    @NotNull(message = "积分价格不能为空")
    @Min(value = 0, message = "积分价格不能小于0")
    private Long pricePoints;

    private Integer grantQuantity;

    private Integer stock;

    private String icon;

    private Integer status;

    private String repurchasePolicy;

    private Integer limitCount;

    private Integer limitWindowSeconds;

    private LocalDateTime beginTime;

    private LocalDateTime endTime;
}
