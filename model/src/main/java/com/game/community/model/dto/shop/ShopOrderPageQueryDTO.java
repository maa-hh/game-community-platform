package com.game.community.model.dto.shop;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.io.Serializable;

/** 用户订单分页查询参数。 */
@Data
public class ShopOrderPageQueryDTO implements Serializable {

    @Min(value = 1, message = "页码必须从1开始")
    private Long page = 1L;

    @Min(value = 1, message = "分页大小必须大于0")
    @Max(value = 100, message = "分页大小不能超过100")
    private Long size = 10L;
}
