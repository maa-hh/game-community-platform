package com.game.community.social.event;

import com.game.community.common.constant.KafkaTopicConstants;
import com.game.community.model.message.ArticleBehaviorMessage;
import com.game.community.common.constant.social.SocialConstants;
import com.game.community.social.service.SocialOutboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ArticleBehaviorProducer {

    private final SocialOutboxService socialOutboxService;

    public void publish(Long articleId,
                        long likeDelta,
                        long commentDelta,
                        long viewDelta,
                        long favoriteDelta,
                        long shareDelta) {
        publish(articleId, likeDelta, commentDelta, viewDelta, favoriteDelta, shareDelta, 0L, 0L);
    }

    public void publish(Long articleId,
                        long likeDelta,
                        long commentDelta,
                        long viewDelta,
                        long favoriteDelta,
                        long shareDelta,
                        long commentLikeDelta,
                        long replyLikeDelta) {
        if (articleId == null) {
            return;
        }
        ArticleBehaviorMessage message = new ArticleBehaviorMessage(
                articleId,
                likeDelta,
                commentDelta,
                viewDelta,
                favoriteDelta,
                shareDelta,
                commentLikeDelta,
                replyLikeDelta,
                System.currentTimeMillis());
        message.setEventId(UUID.randomUUID().toString());
        socialOutboxService.enqueue(SocialConstants.EventType.ARTICLE_BEHAVIOR,
                KafkaTopicConstants.ARTICLE_BEHAVIOR_TOPIC, articleId.toString(), message);
    }

    public void publish(Long articleId, long likeDelta, long commentDelta, long viewDelta) {
        publish(articleId, likeDelta, commentDelta, viewDelta, 0L, 0L);
    }

    public void publishCommentLike(Long articleId, long commentLikeDelta) {
        publish(articleId, 0L, 0L, 0L, 0L, 0L, commentLikeDelta, 0L);
    }

    public void publishReplyLike(Long articleId, long replyLikeDelta) {
        publish(articleId, 0L, 0L, 0L, 0L, 0L, 0L, replyLikeDelta);
    }
}
