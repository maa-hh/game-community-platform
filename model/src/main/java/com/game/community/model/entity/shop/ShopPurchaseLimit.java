package com.game.community.model.entity.shop;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("t_shop_purchase_limit")
public class ShopPurchaseLimit implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long itemId;

    private Integer purchasedCount;

    private Integer reservedCount;

    private LocalDateTime lastPurchaseAt;

    private LocalDateTime windowStartAt;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
