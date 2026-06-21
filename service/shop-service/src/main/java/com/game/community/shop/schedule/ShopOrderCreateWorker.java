package com.game.community.shop.schedule;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.community.common.constant.shop.ShopRedisConstants;
import com.game.community.model.message.ShopOrderCreateMessage;
import com.game.community.shop.service.ShopOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ShopOrderCreateWorker {

    private final StringRedisTemplate stringRedisTemplate;

    private final ObjectMapper objectMapper;

    private final ShopOrderService orderService;

    @Scheduled(fixedDelay = 500)
    public void consumeCreateQueue() {
        for (int i = 0; i < 50; i++) {
            String payload = stringRedisTemplate.opsForList().leftPop(ShopRedisConstants.ORDER_CREATE_QUEUE);
            if (payload == null) {
                return;
            }
            try {
                ShopOrderCreateMessage message = objectMapper.readValue(payload, ShopOrderCreateMessage.class);
                orderService.processCreateMessage(message);
            } catch (Exception e) {
                handleFailure(payload, e);
            }
        }
    }

    private void handleFailure(String payload, Exception e) {
        try {
            ShopOrderCreateMessage message = objectMapper.readValue(payload, ShopOrderCreateMessage.class);
            int attempts = message.getAttempts() == null ? 0 : message.getAttempts();
            if (attempts < 3) {
                message.setAttempts(attempts + 1);
                stringRedisTemplate.opsForList().rightPush(ShopRedisConstants.ORDER_CREATE_QUEUE,
                        objectMapper.writeValueAsString(message));
                log.warn("订单创建异步处理失败，已重试入队: orderNo={}, attempts={}, error={}",
                        message.getOrderNo(), attempts + 1, e.getMessage());
                return;
            }
            orderService.markCreateFailed(message, e.getMessage());
            log.error("订单创建异步处理失败，已标记失败: orderNo={}", message.getOrderNo(), e);
        } catch (Exception parseError) {
            log.error("订单创建消息无法解析，消息将被丢弃: payload={}", payload, parseError);
        }
    }
}
