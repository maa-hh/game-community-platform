package com.game.community.shop.schedule;

import com.game.community.model.entity.shop.ShopDeliveryTask;
import com.game.community.shop.mapper.ShopDeliveryTaskMapper;
import com.game.community.shop.service.ShopOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ShopDeliveryWorker {

    private final ShopDeliveryTaskMapper deliveryTaskMapper;
    private final ShopOrderService orderService;

    @Scheduled(fixedDelayString = "${shop.delivery.poll-ms:500}")
    public void deliverPendingOrders() {
        deliveryTaskMapper.releaseStale(LocalDateTime.now().minusMinutes(2));
        List<ShopDeliveryTask> tasks = deliveryTaskMapper.selectPending(100);
        for (ShopDeliveryTask task : tasks) {
            String token = UUID.randomUUID().toString().replace("-", "");
            if (deliveryTaskMapper.claim(task.getId(), token) != 1) {
                continue;
            }
            try {
                orderService.processDeliveryTask(task, token);
            } catch (Exception e) {
                log.warn("商城权益发放失败，进入重试: orderNo={}, error={}", task.getOrderNo(), e.getMessage());
                orderService.markDeliveryFailed(task, token, e.getMessage());
            }
        }
    }
}
