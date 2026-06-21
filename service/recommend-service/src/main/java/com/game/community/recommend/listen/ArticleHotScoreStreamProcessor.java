package com.game.community.recommend.listen;

import com.game.community.common.constant.KafkaTopicConstants;
import com.game.community.model.message.ArticleBehaviorMessage;
import com.game.community.recommend.service.HotArticleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ArticleHotScoreStreamProcessor {

    private final HotArticleService hotArticleService;

    @KafkaListener(topics = KafkaTopicConstants.ARTICLE_BEHAVIOR_AGGREGATED_TOPIC, groupId = "recommend-service-hot-score")
    public void handleAggregatedMessage(ArticleBehaviorMessage message) {
        if (message == null || message.getArticleId() == null || message.getArticleId() <= 0) {
            return;
        }
        log.info("收到文章行为聚合事件，刷新热度: articleId={}, likeDelta={}, commentDelta={}, viewDelta={}",
                message.getArticleId(), message.getLikeCount(), message.getCommentCount(), message.getViewCount());
        hotArticleService.updateHotScore(message.getArticleId());
    }
}
