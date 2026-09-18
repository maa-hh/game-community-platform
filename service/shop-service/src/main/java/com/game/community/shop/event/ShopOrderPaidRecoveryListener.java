package com.game.community.shop.event;

import com.game.community.common.constant.KafkaTopicConstants;
import com.game.community.model.message.ShopOrderPaidMessage;
import com.game.community.shop.mapper.ShopDeliveryTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 支付事件恢复消费者。事件只负责补建幂等发货任务，实际权益发放仍由数据库抢占 Worker 完成。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShopOrderPaidRecoveryListener {

    private final ShopDeliveryTaskMapper deliveryTaskMapper;

    @KafkaListener(topics = KafkaTopicConstants.SHOP_ORDER_PAID_TOPIC,
            groupId = "${spring.kafka.consumer.group-id:shop-delivery-recovery}")
    public void onOrderPaid(ShopOrderPaidMessage message) {
        if (message == null || message.getOrderNo() == null || message.getOrderNo().isBlank()) {
            log.warn("忽略缺少订单号的商城支付事件");
            return;
        }
        deliveryTaskMapper.insertIfAbsent(message.getOrderNo(), LocalDateTime.now());
    }
}
