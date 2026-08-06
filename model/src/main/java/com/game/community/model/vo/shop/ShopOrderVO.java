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

    private String cosmeticCode;

    private Integer quantity;

    private Long pricePoints;

    private Long totalPoints;

    private Integer grantQuantity;

    private Integer status;

    private String statusText;

    private String failReason;

    private LocalDateTime createTime;

    private LocalDateTime payTime;

    private LocalDateTime completeTime;

    private LocalDateTime expireTime;
}
