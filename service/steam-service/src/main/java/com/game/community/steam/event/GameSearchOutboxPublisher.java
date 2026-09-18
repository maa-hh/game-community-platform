package com.game.community.steam.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.community.common.constant.KafkaTopicConstants;
import com.game.community.model.entity.game.GameSearchOutboxEvent;
import com.game.community.model.message.GameSearchSyncMessage;
import com.game.community.steam.mapper.GameSearchOutboxMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** 游戏搜索 Outbox 投递器，支持多实例抢占、失败重试和死信保留。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GameSearchOutboxPublisher {

    private final GameSearchOutboxMapper outboxMapper;
    private final KafkaTemplate<String, GameSearchSyncMessage> kafkaTemplate;
    private final ObjectMapper objectMapper;

    /** 批量领取待投递事件并异步发送到游戏搜索 Topic。 */
    @Scheduled(fixedDelayString = "${steam.search-outbox.poll-interval-ms:1000}")
    public void publishPending() {
        outboxMapper.releaseStale(LocalDateTime.now().minusMinutes(2));
        List<GameSearchOutboxEvent> events = outboxMapper.selectPending(100);
        for (GameSearchOutboxEvent event : events) {
            String token = UUID.randomUUID().toString().replace("-", "");
            if (outboxMapper.claim(event.getId(), token) != 1) {
                continue;
            }
            publish(event, token);
        }
    }

    /** 将单条 Outbox 事件投递到 Kafka，并根据异步确认更新状态。 */
    private void publish(GameSearchOutboxEvent event, String token) {
        try {
            GameSearchSyncMessage message = objectMapper.readValue(event.getPayload(), GameSearchSyncMessage.class);
            kafkaTemplate.send(KafkaTopicConstants.GAME_SEARCH_SYNC_TOPIC,
                            String.valueOf(event.getAppId()), message)
                    .whenComplete((ignored, error) -> {
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

    /** 记录失败并交给数据库重试策略，超过次数后保留为死信。 */
    private void markFailed(GameSearchOutboxEvent event, String token, Throwable error) {
        log.warn("游戏搜索 Outbox 投递失败: id={}, appId={}", event.getId(), event.getAppId(), error);
        outboxMapper.markFailed(event.getId(), token, error.getMessage());
    }
}
