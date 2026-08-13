package com.game.community.shop.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.community.model.entity.shop.ShopOrderPaidOutbox;
import com.game.community.model.message.ShopOrderPaidMessage;
import com.game.community.shop.mapper.ShopOrderPaidOutboxMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** 事务 Outbox 发布器：支付事务只写库，Kafka 失败由自身重试和 Outbox 退避兜底。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShopOrderPaidOutboxPublisher {

    private final ShopOrderPaidOutboxMapper outboxMapper;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    /** 领取一批支付事件并异步发送到 Kafka；发送失败进入退避重试。 */
    @Scheduled(fixedDelayString = "${shop.kafka.outbox-poll-ms:500}")
    public void publishPending() {
        outboxMapper.releaseStale(LocalDateTime.now().minusMinutes(2));
        List<ShopOrderPaidOutbox> events = outboxMapper.selectPending(100);
        for (ShopOrderPaidOutbox event : events) {
            String token = UUID.randomUUID().toString().replace("-", "");
            if (outboxMapper.claim(event.getId(), token) == 1) {
                publish(event, token);
            }
        }
    }

    private void publish(ShopOrderPaidOutbox event, String token) {
        try {
            ShopOrderPaidMessage message = objectMapper.readValue(event.getPayload(), ShopOrderPaidMessage.class);
            kafkaTemplate.send(event.getTopic(), event.getMessageKey(), message).whenComplete((ignored, error) -> {
                if (error == null) {
                    outboxMapper.markSent(event.getId(), token);
                } else {
                    markFailed(event, token, error);
                }
            });
        } catch (Exception e) {
            markFailed(event, token, e);
        }
    }

    private void markFailed(ShopOrderPaidOutbox event, String token, Throwable error) {
        String message = error == null ? "Kafka 投递失败" : String.valueOf(error.getMessage());
        outboxMapper.markFailed(event.getId(), token, message);
        log.warn("商城支付事件投递失败: orderNo={}, error={}", event.getOrderNo(), message);
    }
}
