package com.game.community.user.event.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.community.common.constant.KafkaTopicConstants;
import com.game.community.model.message.AiTaskRequestMessage;
import com.game.community.model.message.AiTaskResultMessage;
import com.game.community.model.vo.aiagent.ModerationResultVO;
import com.game.community.user.event.executor.AuditTaskExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** 用户服务消费资料审核结果；任务状态 CAS 保证重复消息不会重复应用。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiModerationResultListener {

    private final ObjectMapper objectMapper;
    private final AuditTaskExecutor auditTaskExecutor;

    @KafkaListener(topics = KafkaTopicConstants.AI_TASK_RESULT_TOPIC,
            groupId = "user-ai-moderation")
    public void consume(AiTaskResultMessage message) throws Exception {
        if (message == null || !AiTaskRequestMessage.MODERATION.equals(message.getTaskType())
                || message.getContextPayload() == null) {
            return;
        }
        JsonNode context = objectMapper.readTree(message.getContextPayload());
        if (!AiTaskRequestMessage.CONSUMER_USER.equals(context.path("consumer").asText())
                || message.getSourceId() == null) {
            return;
        }
        ModerationResultVO result;
        if (message.isSuccess()) {
            result = objectMapper.readValue(message.getResultPayload(), ModerationResultVO.class);
        } else {
            result = new ModerationResultVO();
            result.setType(com.game.community.model.enums.aiagent.ModerationContentType.TEXT);
            result.setScore(5);
            result.setResult(com.game.community.model.enums.aiagent.ModerationDecision.HUMAN_REVIEW);
            result.setReason("AI审核任务失败，转人工审核");
        }
        auditTaskExecutor.completeModerationResult(message.getSourceId(), result);
    }
}
