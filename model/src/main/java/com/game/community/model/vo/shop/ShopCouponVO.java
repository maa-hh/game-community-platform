package com.game.community.model.vo.shop;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class ShopCouponVO implements Serializable {

    private Long couponId;

    private String couponName;

    private Integer discountType;

    private Integer discountValue;

    private Integer minAmount;

    private Integer scopeType;

    private Long scopeItemId;

    private Integer scopeProductType;

    private LocalDateTime expireTime;

    private Long userCouponId;

    private Integer status;

    private String statusText;
}
