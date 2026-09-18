package com.game.community.ai.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.community.common.constant.KafkaTopicConstants;
import com.game.community.model.dto.aiagent.AiEmbeddingRequest;
import com.game.community.model.dto.aiagent.AiSearchTermsRequest;
import com.game.community.model.dto.aiagent.ModerationRequest;
import com.game.community.model.message.AiTaskRequestMessage;
import com.game.community.model.message.AiTaskResultMessage;
import com.game.community.ai.service.AiCapabilityService;
import com.game.community.ai.service.AiModerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/** AI 任务唯一执行入口：Kafka 负责削峰，模型调用不会占用业务 HTTP 请求线程。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiTaskListener {

    private final ObjectMapper objectMapper;
    private final AiModerationService moderationService;
    private final AiCapabilityService capabilityService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${ai-agent.kafka.result-send-timeout-ms:5000}")
    private long resultSendTimeoutMs;

    @KafkaListener(topics = KafkaTopicConstants.AI_TASK_REQUEST_TOPIC,
            groupId = "${spring.kafka.consumer.group-id}",
            concurrency = "${spring.kafka.listener.concurrency:3}")
    public void consume(AiTaskRequestMessage message) throws Exception {
        if (message == null || message.getTaskType() == null) {
            log.warn("忽略空 AI 任务消息");
            return;
        }
        AiTaskResultMessage result = new AiTaskResultMessage();
        result.setJobId(message.getJobId());
        result.setTaskType(message.getTaskType());
        result.setSourceId(message.getSourceId());
        result.setContextPayload(message.getContextPayload());
        try {
            Object response = switch (message.getTaskType()) {
                case AiTaskRequestMessage.MODERATION -> moderationService.moderate(
                        objectMapper.readValue(message.getRequestPayload(), ModerationRequest.class));
                case AiTaskRequestMessage.EMBEDDING -> capabilityService.embedding(
                        objectMapper.readValue(message.getRequestPayload(), AiEmbeddingRequest.class));
                case AiTaskRequestMessage.SEARCH_TERMS -> capabilityService.expandSearchTerms(
                        objectMapper.readValue(message.getRequestPayload(), AiSearchTermsRequest.class));
                default -> throw new IllegalArgumentException("未知 AI 任务类型: " + message.getTaskType());
            };
            result.setSuccess(true);
            result.setResultPayload(objectMapper.writeValueAsString(response));
            log.info("AI Kafka 任务完成: jobId={}, type={}, sourceId={}",
                    message.getJobId(), message.getTaskType(), message.getSourceId());
        } catch (Exception e) {
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            log.error("AI Kafka 任务执行失败: jobId={}, type={}, sourceId={}",
                    message.getJobId(), message.getTaskType(), message.getSourceId(), e);
        }
        result.setCompletedAt(System.currentTimeMillis());
        // 结果发送失败必须让请求消息重试，不能在业务侧留下永久 PROCESSING/PENDING。
        kafkaTemplate.send(KafkaTopicConstants.AI_TASK_RESULT_TOPIC,
                message.getJobId(), result).get(resultSendTimeoutMs, TimeUnit.MILLISECONDS);
    }
}
