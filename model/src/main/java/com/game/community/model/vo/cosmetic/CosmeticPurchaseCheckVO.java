package com.game.community.model.vo.cosmetic;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class CosmeticPurchaseCheckVO implements Serializable {

    private Boolean canBuy;

    private String reason;

    private LocalDateTime nextBuyAt;
}
