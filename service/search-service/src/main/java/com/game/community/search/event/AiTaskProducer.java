package com.game.community.search.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.community.common.constant.KafkaTopicConstants;
import com.game.community.model.message.AiTaskRequestMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.UUID;

/** 搜索服务投递 embedding/扩词任务，不让搜索请求线程同步等待模型。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiTaskProducer {
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public String send(String taskType, Long sourceId, Object request, Object context) {
        try {
            AiTaskRequestMessage message = new AiTaskRequestMessage();
            message.setJobId(UUID.randomUUID().toString());
            message.setTaskType(taskType);
            message.setSourceId(sourceId);
            message.setRequestPayload(objectMapper.writeValueAsString(request));
            message.setContextPayload(objectMapper.writeValueAsString(context));
            message.setCreatedAt(System.currentTimeMillis());
            kafkaTemplate.send(KafkaTopicConstants.AI_TASK_REQUEST_TOPIC, messageKey(message, context), message)
                    .get(5, TimeUnit.SECONDS);
            return message.getJobId();
        } catch (Exception e) {
            throw new IllegalStateException("搜索 AI 任务投递失败", e);
        }
    }

    /** 查询向量不能全部使用 sourceId=0，否则所有查询会集中到同一 Kafka 分区。 */
    private String messageKey(AiTaskRequestMessage message, Object context) {
        if (context instanceof com.game.community.model.message.SearchAiContext searchContext
                && com.game.community.model.message.SearchAiContext.QUERY_EMBEDDING
                .equals(searchContext.getOperation())) {
            return "query:" + Integer.toHexString(searchContext.getQuery() == null
                    ? 0 : searchContext.getQuery().hashCode());
        }
        return String.valueOf(message.getSourceId());
    }
}
