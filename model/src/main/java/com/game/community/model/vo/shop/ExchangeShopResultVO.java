package com.game.community.model.vo.shop;

import lombok.Data;

import java.io.Serializable;

@Data
public class ExchangeShopResultVO implements Serializable {

    private String orderNo;

    private String cosmeticCode;

    private Integer grantQuantity;

    private Long pointsBalance;

    private Integer status;

    private String statusText;
}
