package com.game.community.search.listener;

import com.game.community.common.constant.KafkaTopicConstants;
import com.game.community.model.message.GameSearchSyncMessage;
import com.game.community.search.service.GameSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class GameSearchSyncListener {

    private final GameSearchService gameSearchService;

    @KafkaListener(topics = KafkaTopicConstants.GAME_SEARCH_SYNC_TOPIC,
            groupId = "search-service-game-sync",
            concurrency = "${spring.kafka.listener.concurrency:3}")
    public void onMessage(GameSearchSyncMessage message) {
        if (message == null || message.getAppId() == null) {
            return;
        }
        try {
            if (GameSearchSyncMessage.DELETE.equals(message.getAction())) {
                gameSearchService.delete(message.getAppId());
            } else if (message.getGame() != null) {
                gameSearchService.index(message.getGame(), message.getEventTime());
            }
        } catch (Exception e) {
            log.error("游戏搜索索引同步失败: appId={}, action={}",
                    message.getAppId(), message.getAction(), e);
            throw e;
        }
    }
}
