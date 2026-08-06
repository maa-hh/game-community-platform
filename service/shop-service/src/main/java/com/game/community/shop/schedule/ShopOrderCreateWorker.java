package com.game.community.shop.schedule;

import com.game.community.model.entity.shop.ShopOrder;
import com.game.community.shop.mapper.ShopOrderMapper;
import com.game.community.shop.service.ShopOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ShopOrderCreateWorker {

    private final ShopOrderMapper orderMapper;
    private final ShopOrderService orderService;

    @Scheduled(fixedDelayString = "${shop.order.create-poll-ms:500}")
    public void repairCreatingOrders() {
        for (ShopOrder order : orderMapper.selectCreatingOrders(100)) {
            try {
                orderService.processCreatingOrder(order.getOrderNo());
            } catch (Exception e) {
                log.warn("商城订单创建失败，进入补偿: orderNo={}, error={}", order.getOrderNo(), e.getMessage());
                orderService.markCreatingFailed(order.getOrderNo(), e.getMessage());
            }
        }
    }
}
