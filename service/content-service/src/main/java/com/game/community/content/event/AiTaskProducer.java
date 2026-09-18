package com.game.community.content.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.community.common.constant.KafkaTopicConstants;
import com.game.community.model.message.AiTaskRequestMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.UUID;

/** 内容服务向 AI 任务队列投递消息，不阻塞发布任务线程。 */
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
            message.setContextPayload(context == null ? null : objectMapper.writeValueAsString(context));
            message.setCreatedAt(System.currentTimeMillis());
            kafkaTemplate.send(KafkaTopicConstants.AI_TASK_REQUEST_TOPIC, String.valueOf(sourceId), message)
                    .get(5, TimeUnit.SECONDS);
            log.info("AI 任务已投递: jobId={}, type={}, sourceId={}", message.getJobId(), taskType, sourceId);
            return message.getJobId();
        } catch (Exception e) {
            throw new IllegalStateException("AI 任务投递失败", e);
        }
    }
}
