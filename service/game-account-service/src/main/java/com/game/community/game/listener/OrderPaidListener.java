package com.game.community.game.listener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.community.common.constant.KafkaTopicConstants;
import com.game.community.game.service.GameDeliveryService;
import com.game.community.model.message.ShopOrderPaidMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderPaidListener {

    private final ObjectMapper objectMapper;
    private final GameDeliveryService gameDeliveryService;

    @KafkaListener(topics = KafkaTopicConstants.SHOP_ORDER_PAID_TOPIC, groupId = "game-account-consumer-group")
    public void handleOrderPaid(String payload) {
        try {
            ShopOrderPaidMessage message = objectMapper.readValue(payload, ShopOrderPaidMessage.class);
            gameDeliveryService.deliver(message);
        } catch (JsonProcessingException e) {
            log.error("解析商城支付成功消息失败: payload={}", payload, e);
        } catch (RuntimeException e) {
            log.error("处理商城支付成功消息失败: payload={}", payload, e);
            throw e;
        }
    }
}
