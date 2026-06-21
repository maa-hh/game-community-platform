package com.game.community.model.message;

import lombok.Data;

import java.io.Serializable;

@Data
public class ShopOrderPaidMessage implements Serializable {

    private String orderNo;

    private Long userId;

    private Long itemId;

    private Integer productType;

    private Integer price;

    private Long couponId;

    private String businessCode;

    private Integer quantity;
}

