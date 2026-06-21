package com.game.community.model.dto.shop;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

@Data
public class PayShopOrderDTO implements Serializable {

    @NotBlank(message = "订单号不能为空")
    private String orderNo;
}
