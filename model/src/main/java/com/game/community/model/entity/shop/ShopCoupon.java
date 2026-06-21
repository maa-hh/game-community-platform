package com.game.community.model.entity.shop;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("t_shop_coupon")
public class ShopCoupon implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private Integer discountType;

    private Integer discountValue;

    private Integer minAmount;

    private Integer scopeType;

    private Long scopeItemId;

    private Integer scopeProductType;

    private LocalDateTime expireTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
