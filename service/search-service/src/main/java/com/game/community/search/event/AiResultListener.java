package com.game.community.search.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.community.common.constant.KafkaTopicConstants;
import com.game.community.model.message.AiTaskRequestMessage;
import com.game.community.model.message.AiTaskResultMessage;
import com.game.community.model.message.SearchAiContext;
import com.game.community.model.vo.aiagent.AiEmbeddingResponseVO;
import com.game.community.model.vo.aiagent.AiSearchTermsResponseVO;
import com.game.community.search.service.ElasticsearchService;
import com.game.community.search.service.impl.AiSuggestTermServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** 搜索服务统一消费 AI 结果：向量回写 ES/Redis，扩词回写建议词表。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiResultListener {

    private final ObjectMapper objectMapper;
    private final ElasticsearchService elasticsearchService;
    private final AiSuggestTermServiceImpl aiSuggestTermService;
    private final StringRedisTemplate stringRedisTemplate;

    @KafkaListener(topics = KafkaTopicConstants.AI_TASK_RESULT_TOPIC,
            groupId = "search-ai-result")
    public void consume(AiTaskResultMessage message) throws Exception {
        if (message == null || message.getContextPayload() == null) {
            return;
        }
        SearchAiContext context = objectMapper.readValue(message.getContextPayload(), SearchAiContext.class);
        if (!AiTaskRequestMessage.CONSUMER_SEARCH.equals(context.getConsumer())) {
            return;
        }
        if (!message.isSuccess()) {
            clearPendingQueryEmbedding(message, context);
            log.warn("搜索 AI 任务失败: type={}, sourceId={}, reason={}",
                    message.getTaskType(), message.getSourceId(), message.getErrorMessage());
            return;
        }
        if (AiTaskRequestMessage.EMBEDDING.equals(message.getTaskType())) {
            AiEmbeddingResponseVO response = objectMapper.readValue(message.getResultPayload(), AiEmbeddingResponseVO.class);
            if (response.getVector() == null || response.getVector().isEmpty()) {
                return;
            }
            if (SearchAiContext.ARTICLE_EMBEDDING.equals(context.getOperation())
                    && message.getSourceId() != null && message.getSourceId() > 0) {
                elasticsearchService.updateArticleEmbedding(message.getSourceId(), response.getVector());
            } else if (SearchAiContext.QUERY_EMBEDDING.equals(context.getOperation())) {
                String key = "search:ai:embedding:" + sha256(context.getQuery());
                stringRedisTemplate.opsForValue().set(key,
                        objectMapper.writeValueAsString(response.getVector()), java.time.Duration.ofHours(1));
            }
            return;
        }
        if (AiTaskRequestMessage.SEARCH_TERMS.equals(message.getTaskType())
                && SearchAiContext.SEARCH_TERMS.equals(context.getOperation())
                && message.getSourceId() != null && context.getDocument() != null) {
            AiSearchTermsResponseVO response = objectMapper.readValue(
                    message.getResultPayload(), AiSearchTermsResponseVO.class);
            if (response.getTerms() != null && !response.getTerms().isEmpty()) {
                aiSuggestTermService.writeIfCurrent(message.getSourceId(), context.getDocument(), response.getTerms());
            }
        }
    }

    private void clearPendingQueryEmbedding(AiTaskResultMessage message, SearchAiContext context) {
        if (AiTaskRequestMessage.EMBEDDING.equals(message.getTaskType())
                && SearchAiContext.QUERY_EMBEDDING.equals(context.getOperation())
                && context.getQuery() != null) {
            stringRedisTemplate.delete("search:ai:embedding:pending:" + sha256(context.getQuery()));
        }
    }

    private String sha256(String text) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte value : digest) {
                result.append(String.format("%02x", value));
            }
            return result.toString();
        } catch (Exception e) {
            throw new IllegalStateException("查询向量键生成失败", e);
        }
    }
}
