package com.game.community.model.vo.shop;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class ShopOrderVO implements Serializable {

    private String orderNo;

    private String requestId;

    private Long itemId;

    private String itemName;

    private String itemIcon;

    private Integer productType;

    private Integer quantity;

    private Integer payType;

    private Integer originalPrice;

    private Integer discountAmount;

    private Integer finalPrice;

    private Long couponId;

    private Long userCouponId;

    private String businessCode;

    private Integer status;

    private String statusText;

    private String failReason;

    private LocalDateTime createTime;

    private LocalDateTime payTime;

    private LocalDateTime expireTime;
}
