package com.game.community.model.vo.shop;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class ShopItemVO implements Serializable {

    private Long id;

    private String name;

    private String description;

    private Integer price;

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
