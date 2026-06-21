package com.game.community.content.event;

import com.game.community.common.constant.KafkaTopicConstants;
import com.game.community.model.message.ArticleSearchSyncMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class ArticleSearchSyncProducer {

    private final KafkaTemplate<String, ArticleSearchSyncMessage> kafkaTemplate;

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
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    doPublish(articleId, action);
                }
            });
            return;
        }
        doPublish(articleId, action);
    }

    private void doPublish(Long articleId, String action) {
        ArticleSearchSyncMessage message = new ArticleSearchSyncMessage(articleId, action, LocalDateTime.now());
        kafkaTemplate.send(KafkaTopicConstants.ARTICLE_SEARCH_SYNC_TOPIC, String.valueOf(articleId), message)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.warn("文章搜索同步消息发送失败: articleId={}, action={}", articleId, action, ex);
                        return;
                    }
                    log.info("文章搜索同步消息发送成功: articleId={}, action={}", articleId, action);
                });
    }
}
