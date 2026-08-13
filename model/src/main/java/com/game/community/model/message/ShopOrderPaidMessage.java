package com.game.community.model.message;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 商城支付成功事件；消费者必须使用 orderNo 做幂等键。 */
@Data
@NoArgsConstructor
public class ShopOrderPaidMessage implements Serializable {

    private String eventId;
    private Integer schemaVersion = 1;
    private String orderNo;
    private Long userId;
    private Long itemId;
    private String cosmeticCode;
    private Integer quantity;
    private Integer grantQuantity;
    private LocalDateTime paidAt;
}
