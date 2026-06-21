package com.game.community.search.listener;

import com.game.community.common.constant.KafkaTopicConstants;
import com.game.community.model.message.ArticleSearchSyncMessage;
import com.game.community.search.service.ArticleSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ArticleSyncListener {

    private final ArticleSyncService articleSyncService;

    @KafkaListener(topics = KafkaTopicConstants.ARTICLE_SEARCH_SYNC_TOPIC, groupId = "search-service-sync")
    public void onMessage(ArticleSearchSyncMessage message) {
        if (message == null || message.getArticleId() == null) {
            return;
        }
        log.info("收到文章搜索同步消息: articleId={}, action={}", message.getArticleId(), message.getAction());
        if (ArticleSearchSyncMessage.DELETE.equals(message.getAction())) {
            articleSyncService.deleteArticle(message.getArticleId());
            return;
        }
        articleSyncService.syncArticle(message.getArticleId());
    }
}
