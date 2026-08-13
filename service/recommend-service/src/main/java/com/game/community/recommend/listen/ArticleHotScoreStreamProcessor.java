package com.game.community.recommend.listen;

import com.game.community.common.constant.KafkaTopicConstants;
import com.game.community.common.constant.RecommendConstants;
import com.game.community.model.message.ArticleBehaviorMessage;
import com.game.community.recommend.service.HotRankService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ArticleHotScoreStreamProcessor {

    private final HotRankService hotRankService;

    @KafkaListener(topics = KafkaTopicConstants.ARTICLE_BEHAVIOR_TOPIC,
            groupId = "${recommend.kafka.consumer-group:" + RecommendConstants.BEHAVIOR_CONSUMER_GROUP + "}",
            containerFactory = "articleBehaviorKafkaListenerContainerFactory")
    public void handleBehaviorMessage(ConsumerRecord<String, ArticleBehaviorMessage> record) {
        ArticleBehaviorMessage message = record == null ? null : record.value();
        if (message == null || message.getArticleId() == null || message.getArticleId() <= 0) {
            return;
        }
        if (message.getEventId() == null || message.getEventId().isBlank()) {
            // 兼容旧消息：Kafka offset 在同一分区内稳定，可作为临时幂等键。
            message.setEventId(record.topic() + ":" + record.partition() + ":" + record.offset());
        }
        log.debug("处理文章行为事件: eventId={}, articleId={}", message.getEventId(), message.getArticleId());
        hotRankService.applyBehaviorDelta(message);
    }
}
