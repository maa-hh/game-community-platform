package com.game.community.content.event;

import com.alibaba.fastjson2.JSON;
import com.game.community.content.mapper.ContentOutboxMapper;
import com.game.community.content.service.ArticleContentService;
import com.game.community.content.service.ChunkUploadService;
import com.game.community.content.util.ArticleMediaHelper;
import com.game.community.feign.SteamFeignClient;
import com.game.community.feign.SocialFeignClient;
import com.game.community.model.entity.content.ContentOutboxEvent;
import com.game.community.model.dto.social.PublishArticleFeedDTO;
import com.game.community.model.message.ArticleSearchSyncMessage;
import com.game.community.model.message.ModerationTaskMessage;
import com.game.community.model.message.NotificationEventMessage;
import com.game.community.model.base.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Outbox 投递器：Kafka 使用异步确认，远程 Feed 使用隔离线程池；失败统一回退到数据库重试。
 */
@Slf4j
@Component
public class ContentOutboxPublisher {

    private final ContentOutboxMapper contentOutboxMapper;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final SocialFeignClient socialFeignClient;
    private final SteamFeignClient steamFeignClient;
    private final ArticleContentService articleContentService;
    private final ChunkUploadService chunkUploadService;
    private final ArticleMediaHelper articleMediaHelper;

    private final Executor remoteExecutor;

    @Value("${social.internal-token:${SOCIAL_INTERNAL_TOKEN:}}")
    private String socialInternalToken;

    public ContentOutboxPublisher(ContentOutboxMapper contentOutboxMapper,
                                   KafkaTemplate<String, Object> kafkaTemplate,
                                   SocialFeignClient socialFeignClient,
                                   SteamFeignClient steamFeignClient,
                                   ArticleContentService articleContentService,
                                   ChunkUploadService chunkUploadService,
                                   ArticleMediaHelper articleMediaHelper,
                                   @Qualifier("contentRemoteExecutor") Executor remoteExecutor) {
        this.contentOutboxMapper = contentOutboxMapper;
        this.kafkaTemplate = kafkaTemplate;
        this.socialFeignClient = socialFeignClient;
        this.steamFeignClient = steamFeignClient;
        this.articleContentService = articleContentService;
        this.chunkUploadService = chunkUploadService;
        this.articleMediaHelper = articleMediaHelper;
        this.remoteExecutor = remoteExecutor;
    }

    @Scheduled(fixedDelayString = "${content.outbox.poll-interval-ms:1000}")
    public void publishPending() {
        contentOutboxMapper.releaseStale(LocalDateTime.now().minusMinutes(2));
        List<ContentOutboxEvent> events = contentOutboxMapper.selectPending(100);
        for (ContentOutboxEvent event : events) {
            String token = UUID.randomUUID().toString().replace("-", "");
            if (contentOutboxMapper.claim(event.getId(), token) != 1) {
                continue;
            }
            publish(event, token);
        }
    }

    private void publish(ContentOutboxEvent event, String token) {
        try {
            switch (event.getEventType()) {
                case "ARTICLE_SEARCH_SYNC" -> sendKafka(event, token,
                        JSON.parseObject(event.getPayload(), ArticleSearchSyncMessage.class));
                case "NOTIFICATION_EVENT" -> sendKafka(event, token,
                        JSON.parseObject(event.getPayload(), NotificationEventMessage.class));
                case "MODERATION_TASK" -> sendKafka(event, token,
                        JSON.parseObject(event.getPayload(), ModerationTaskMessage.class));
                case "SOCIAL_FEED" -> CompletableFuture.runAsync(() -> sendSocialFeed(event, token), remoteExecutor)
                        .exceptionally(error -> {
                            markFailed(event, token, error);
                            return null;
                        });
                case "GAME_DISCUSS_SYNC" -> CompletableFuture.runAsync(() -> sendGameDiscussSync(event, token), remoteExecutor)
                        .exceptionally(error -> {
                            markFailed(event, token, error);
                            return null;
                        });
                case "ARTICLE_CLEANUP" -> CompletableFuture.runAsync(() -> cleanupArticle(event, token), remoteExecutor)
                        .exceptionally(error -> {
                            markFailed(event, token, error);
                            return null;
                        });
                default -> throw new IllegalArgumentException("未知 Outbox 事件类型: " + event.getEventType());
            }
        } catch (Exception e) {
            markFailed(event, token, e);
        }
    }

    private void sendKafka(ContentOutboxEvent event, String token, Object payload) {
        String topic = event.getTopic();
        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("Outbox Kafka topic 为空");
        }
        kafkaTemplate.send(topic, event.getMessageKey(), payload)
                .whenComplete((ignored, error) -> {
                    if (error == null) {
                        contentOutboxMapper.markSent(event.getId(), token);
                    } else {
                        markFailed(event, token, error);
                    }
                });
    }

    private void sendSocialFeed(ContentOutboxEvent event, String token) {
        Map<String, Object> payload = JSON.parseObject(event.getPayload());
        Long authorId = toLong(payload.get("authorId"));
        Long articleId = toLong(payload.get("articleId"));
        String publishedTime = String.valueOf(payload.get("publishedTime"));
        PublishArticleFeedDTO request = new PublishArticleFeedDTO();
        request.setAuthorId(authorId);
        request.setArticleId(articleId);
        request.setPublishedTime(LocalDateTime.parse(publishedTime));
        Result<Void> result = socialFeignClient.publishArticleToFollowers(request, socialInternalToken);
        if (result == null || result.getCode() == null || result.getCode() != 200) {
            throw new IllegalStateException("社交 Feed 服务返回失败");
        }
        contentOutboxMapper.markSent(event.getId(), token);
    }

    private void sendGameDiscussSync(ContentOutboxEvent event, String token) {
        Map<String, Object> payload = JSON.parseObject(event.getPayload());
        List<Long> appIds = JSON.parseArray(JSON.toJSONString(payload.get("appIds")), Long.class);
        Result<Void> result = steamFeignClient.syncDiscussCount(appIds);
        if (result == null || result.getCode() == null || result.getCode() != 200) {
            throw new IllegalStateException("Steam 讨论数同步失败");
        }
        contentOutboxMapper.markSent(event.getId(), token);
    }

    private void cleanupArticle(ContentOutboxEvent event, String token) {
        Map<String, Object> payload = JSON.parseObject(event.getPayload());
        Long articleId = toLong(payload.get("articleId"));
        Long userId = toLong(payload.get("userId"));
        List<String> refs = JSON.parseArray(JSON.toJSONString(payload.get("refs")), String.class);
        articleMediaHelper.deleteRefsStrict(refs);
        chunkUploadService.abortByArticleId(articleId, userId, true);
        articleContentService.deleteByArticleId(articleId);
        contentOutboxMapper.markSent(event.getId(), token);
    }

    private void markFailed(ContentOutboxEvent event, String token, Throwable error) {
        String message = error == null ? "未知错误" : String.valueOf(error.getMessage());
        contentOutboxMapper.markFailed(event.getId(), token, message);
        log.warn("内容 Outbox 投递失败: id={}, type={}, error={}",
                event.getId(), event.getEventType(), message);
    }

    private Long toLong(Object value) {
        return value == null ? null : Long.valueOf(String.valueOf(value));
    }
}
