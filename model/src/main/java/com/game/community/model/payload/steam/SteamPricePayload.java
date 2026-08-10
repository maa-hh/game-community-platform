package com.game.community.model.payload.steam;

import lombok.Data;

import java.io.Serializable;

/** Steam 价格接口返回的内部载荷。 */
@Data
public class SteamPricePayload implements Serializable {

    private Boolean free;

    private String currency;

    private Integer initial;

    private Integer finalPrice;

    private Integer discountPercent;

    private Long discountEndAt;

    private String formatted;
}
