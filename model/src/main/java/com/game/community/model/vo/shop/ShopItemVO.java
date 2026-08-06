package com.game.community.model.vo.shop;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class ShopItemVO implements Serializable {

    private Long id;

    private String name;

    private String description;

    private String cosmeticCode;

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

    private Boolean owned;

    private Boolean equipped;

    private Boolean canBuy;

    private String cannotBuyReason;

    private LocalDateTime nextBuyAt;
}
