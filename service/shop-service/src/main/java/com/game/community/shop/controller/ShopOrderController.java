package com.game.community.shop.controller;

import com.game.community.common.annotation.LoginCheck;
import com.game.community.model.base.Result;
import com.game.community.model.dto.shop.CreateShopOrderDTO;
import com.game.community.model.dto.shop.PayShopOrderDTO;
import com.game.community.model.dto.shop.ShopOrderPageQueryDTO;
import com.game.community.model.vo.shop.ShopOrderVO;
import com.game.community.shop.service.ShopOrderService;
import com.game.community.shop.sentinel.ShopSentinelBlockHandler;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.game.community.utils.ThreadLocal.UserThreadLocal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/shop/order")
@RequiredArgsConstructor
public class ShopOrderController {

    private final ShopOrderService orderService;

    @LoginCheck
    @SentinelResource(value = "shop.order.create", blockHandlerClass = ShopSentinelBlockHandler.class,
            blockHandler = "handle")
    @PostMapping
    public Result<ShopOrderVO> createOrder(@Valid @RequestBody CreateShopOrderDTO dto) {
        return Result.success(orderService.createOrder(UserThreadLocal.getUserId(), dto));
    }

    @LoginCheck
    @GetMapping("/{orderNo}")
    public Result<ShopOrderVO> getOrder(@PathVariable("orderNo") String orderNo) {
        return Result.success(orderService.getOrder(UserThreadLocal.getUserId(), orderNo));
    }

    @LoginCheck
    @GetMapping("/page")
    public Object pageOrders(@Valid ShopOrderPageQueryDTO query) {
        return orderService.pageOrders(UserThreadLocal.getUserId(), query.getPage(), query.getSize());
    }

    @LoginCheck
    @SentinelResource(value = "shop.order.pay", blockHandlerClass = ShopSentinelBlockHandler.class,
            blockHandler = "handle")
    @PostMapping("/pay")
    public Result<Void> payOrder(@Valid @RequestBody PayShopOrderDTO dto) {
        orderService.payOrder(UserThreadLocal.getUserId(), dto);
        return Result.success(null);
    }

    @LoginCheck
    @PostMapping("/{orderNo}/cancel")
    public Result<Void> cancelOrder(@PathVariable("orderNo") String orderNo) {
        orderService.cancelOrder(UserThreadLocal.getUserId(), orderNo);
        return Result.success(null);
    }

}
