package com.game.community.shop.schedule;

import com.game.community.shop.service.ShopOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ShopOrderExpireWorker {

    private final ShopOrderService orderService;

    @Scheduled(fixedDelay = 5000)
    public void cancelExpiredOrders() {
        orderService.cancelExpiredOrders();
    }
}
