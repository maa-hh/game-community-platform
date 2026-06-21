package com.game.community.social.event;

import com.game.community.common.constant.KafkaTopicConstants;
import com.game.community.model.message.ArticleBehaviorMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ArticleBehaviorProducer {

    private final KafkaTemplate<String, ArticleBehaviorMessage> kafkaTemplate;

    public void publish(Long articleId, long likeDelta, long commentDelta, long viewDelta) {
        if (articleId == null) {
            return;
        }
        ArticleBehaviorMessage message = new ArticleBehaviorMessage(articleId, likeDelta, commentDelta, viewDelta);
        try {
            kafkaTemplate.send(KafkaTopicConstants.ARTICLE_BEHAVIOR_TOPIC, articleId.toString(), message)
                    .whenComplete((result, error) -> {
                        if (error != null) {
                            log.warn("发送文章行为事件失败: articleId={}, error={}", articleId, error.getMessage());
                        }
                    });
        } catch (RuntimeException e) {
            log.warn("发送文章行为事件异常: articleId={}, error={}", articleId, e.getMessage());
        }
    }
}
