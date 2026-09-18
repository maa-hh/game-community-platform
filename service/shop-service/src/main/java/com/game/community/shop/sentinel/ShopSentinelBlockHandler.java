package com.game.community.shop.sentinel;

import com.game.community.model.base.Result;
import com.game.community.model.dto.shop.CreateShopOrderDTO;
import com.game.community.model.dto.shop.ExchangeShopItemDTO;
import com.game.community.model.dto.shop.PayShopOrderDTO;
import com.game.community.model.dto.shop.ShopItemPageQueryDTO;
import com.alibaba.csp.sentinel.slots.block.BlockException;

/** 商城接口被 Sentinel 限流时返回统一业务响应。 */
public final class ShopSentinelBlockHandler {

    public static Object handle(BlockException exception) {
        return Result.error(429, "商城请求过于频繁，请稍后重试");
    }

    public static Object handle(ShopItemPageQueryDTO query, BlockException exception) {
        return handle(exception);
    }

    public static Object handle(CreateShopOrderDTO dto, BlockException exception) {
        return handle(exception);
    }

    public static Object handle(PayShopOrderDTO dto, BlockException exception) {
        return handle(exception);
    }

    public static Object handle(ExchangeShopItemDTO dto, BlockException exception) {
        return handle(exception);
    }

    private ShopSentinelBlockHandler() {
    }
}
