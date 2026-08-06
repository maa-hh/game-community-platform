package com.game.community.model.entity.shop;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("t_shop_delivery_task")
public class ShopDeliveryTask implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;

    private Integer status;

    private Integer retryCount;

    private LocalDateTime nextRetryTime;

    private String lockToken;

    private LocalDateTime lockTime;

    private String lastError;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
