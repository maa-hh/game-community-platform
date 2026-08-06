package com.game.community.model.entity.shop;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("t_shop_item")
public class ShopItem implements Serializable {

    @TableId(type = IdType.AUTO)
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

    @Version
    private Integer version;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
