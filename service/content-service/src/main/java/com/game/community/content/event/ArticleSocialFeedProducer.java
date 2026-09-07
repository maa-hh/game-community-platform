package com.game.community.content.event;

import com.game.community.content.service.ContentOutboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/** 将粉丝 Feed 推送变为可重试的 Outbox 事件。 */
@Component
@RequiredArgsConstructor
public class ArticleSocialFeedProducer {

    private final ContentOutboxService contentOutboxService;

    public void publish(Long authorId, Long articleId, LocalDateTime publishedTime) {
        contentOutboxService.enqueue(
                "social-feed:" + articleId + ":" + UUID.randomUUID(),
                "SOCIAL_FEED",
                null,
                String.valueOf(articleId),
                Map.of("authorId", authorId, "articleId", articleId,
                        "publishedTime", publishedTime.toString()));
    }

    public void remove(Long articleId) {
        contentOutboxService.enqueue(
                "social-feed-remove:" + articleId + ":" + UUID.randomUUID(),
                "SOCIAL_FEED_REMOVE",
                null,
                String.valueOf(articleId),
                Map.of("articleId", articleId));
    }
}
