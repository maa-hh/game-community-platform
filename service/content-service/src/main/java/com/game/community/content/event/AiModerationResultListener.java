package com.game.community.content.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.community.common.constant.KafkaTopicConstants;
import com.game.community.content.service.ArticleAsyncService;
import com.game.community.model.message.AiTaskRequestMessage;
import com.game.community.model.message.AiTaskResultMessage;
import com.game.community.model.message.ArticleModerationContext;
import com.game.community.model.vo.aiagent.ModerationResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** 内容服务消费 AI 审核结果并完成文章状态流转。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiModerationResultListener {

    private final ObjectMapper objectMapper;
    private final ArticleAsyncService articleAsyncService;

    @KafkaListener(topics = KafkaTopicConstants.AI_TASK_RESULT_TOPIC,
            groupId = "content-ai-moderation")
    public void consume(AiTaskResultMessage message) throws Exception {
        if (message == null || !AiTaskRequestMessage.MODERATION.equals(message.getTaskType())
                || message.getContextPayload() == null
                || !AiTaskRequestMessage.CONSUMER_CONTENT.equals(
                objectMapper.readTree(message.getContextPayload()).path("consumer").asText())) {
            return;
        }
        ArticleModerationContext context = objectMapper.readValue(
                message.getContextPayload(), ArticleModerationContext.class);
        if (!message.isSuccess()) {
            log.warn("文章 AI 任务失败，转人工审核: articleId={}, reason={}",
                    context.getArticleId(), message.getErrorMessage());
            ModerationResultVO fallback = new ModerationResultVO();
            fallback.setType(com.game.community.model.enums.aiagent.ModerationContentType.ARTICLE);
            fallback.setKeywordAudit(com.game.community.model.enums.aiagent.ModerationCheckResult.PASS);
            fallback.setAiAudit(com.game.community.model.enums.aiagent.ModerationCheckResult.NOT_EXECUTED);
            fallback.setScore(5);
            fallback.setResult(com.game.community.model.enums.aiagent.ModerationDecision.HUMAN_REVIEW);
            fallback.setReason("AI审核任务失败，转人工审核");
            articleAsyncService.completeAuditAndPublish(context, fallback);
            return;
        }
        ModerationResultVO result = objectMapper.readValue(message.getResultPayload(), ModerationResultVO.class);
        articleAsyncService.completeAuditAndPublish(context, result);
    }
}
