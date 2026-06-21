package com.game.community.model.entity.shop;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("t_shop_order")
public class ShopOrder implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;

    private String requestId;

    private Long userId;

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

    private String failReason;

    private LocalDateTime createTime;

    private LocalDateTime payTime;

    private LocalDateTime expireTime;

    private LocalDateTime updateTime;
}
