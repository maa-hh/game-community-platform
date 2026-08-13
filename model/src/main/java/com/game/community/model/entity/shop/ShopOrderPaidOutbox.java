package com.game.community.model.entity.shop;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("t_shop_order_paid_outbox")
public class ShopOrderPaidOutbox implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String eventId;
    private String orderNo;
    private String topic;
    private String messageKey;
    private String payload;
    private Integer status;
    private Integer retryCount;
    private LocalDateTime nextRetryTime;
    private String lockToken;
    private LocalDateTime lockTime;
    private String lastError;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
