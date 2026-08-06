package com.game.community.content.event;

import com.game.community.common.constant.KafkaTopicConstants;
import com.game.community.model.message.ArticleSearchSyncMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import com.game.community.content.service.ContentOutboxService;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ArticleSearchSyncProducer {

    private final ContentOutboxService contentOutboxService;

    public void upsert(Long articleId) {
        publish(articleId, ArticleSearchSyncMessage.UPSERT);
    }

    public void delete(Long articleId) {
        publish(articleId, ArticleSearchSyncMessage.DELETE);
    }

    private void publish(Long articleId, String action) {
        if (articleId == null) {
            return;
        }
        ArticleSearchSyncMessage message = new ArticleSearchSyncMessage(articleId, action, java.time.LocalDateTime.now());
        contentOutboxService.enqueue("article-search:" + articleId + ":" + action + ":" + UUID.randomUUID(),
                "ARTICLE_SEARCH_SYNC", KafkaTopicConstants.ARTICLE_SEARCH_SYNC_TOPIC,
                String.valueOf(articleId), message);
    }
}
