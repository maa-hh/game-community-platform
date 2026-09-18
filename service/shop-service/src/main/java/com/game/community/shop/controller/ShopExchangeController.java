package com.game.community.shop.controller;

import com.game.community.common.annotation.LoginCheck;
import com.game.community.model.base.Result;
import com.game.community.model.dto.shop.ExchangeShopItemDTO;
import com.game.community.model.vo.shop.ExchangeShopResultVO;
import com.game.community.shop.service.ShopOrderService;
import com.game.community.shop.sentinel.ShopSentinelBlockHandler;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.game.community.utils.ThreadLocal.UserThreadLocal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/shop/exchange")
@RequiredArgsConstructor
public class ShopExchangeController {

    private final ShopOrderService orderService;

    @LoginCheck
    @SentinelResource(value = "shop.exchange", blockHandlerClass = ShopSentinelBlockHandler.class,
            blockHandler = "handle")
    @PostMapping
    public Result<ExchangeShopResultVO> exchange(@Valid @RequestBody ExchangeShopItemDTO dto) {
        return Result.success(orderService.exchange(UserThreadLocal.getUserId(), dto));
    }
}
