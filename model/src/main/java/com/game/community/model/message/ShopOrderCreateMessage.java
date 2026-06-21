package com.game.community.model.message;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class ShopOrderCreateMessage implements Serializable {

    private String orderNo;

    private String requestId;

    private Long userId;

    private Long itemId;

    private Integer quantity;

    private Integer payType;

    private Integer originalPrice;

    private Integer discountAmount;

    private Integer finalPrice;

    private Long couponId;

    private Long userCouponId;

    private String businessCode;

    private LocalDateTime expireTime;

    private Integer attempts;
}
