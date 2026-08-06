package com.game.community.model.vo.game;

import lombok.Data;

import java.io.Serializable;

@Data
public class GamePriceVO implements Serializable {

    private Boolean free;

    private String currency;

    /** 原价，单位：分 */
    private Integer initial;

    /** 现价，单位：分 */
    private Integer finalPrice;

    private Integer discountPercent;

    /** 促销截止 Unix 秒 */
    private Long discountEndAt;

    private String formatted;
}
