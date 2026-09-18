package com.game.community.user.event.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.community.common.constant.KafkaTopicConstants;
import com.game.community.model.message.AiTaskRequestMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.UUID;

/** 用户服务向 AI 任务队列投递资料审核请求。 */
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
            log.info("用户资料 AI 任务已投递: jobId={}, taskId={}", message.getJobId(), sourceId);
            return message.getJobId();
        } catch (Exception e) {
            throw new IllegalStateException("AI 资料审核任务投递失败", e);
        }
    }
}
