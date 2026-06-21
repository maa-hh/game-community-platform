package com.game.community.ai.service.impl;

import com.game.community.ai.service.AiChatService;
import com.game.community.ai.service.ChatMemoryService;
import com.game.community.ai.service.KnowledgeRetrievalService;
import com.game.community.model.vo.aiagent.AiChatResponseVO;
import com.game.community.model.vo.aiagent.AiKnowledgeSearchHitVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AiChatServiceImpl implements AiChatService {

    private static final String SYSTEM_PROMPT = """
            你是游戏社区的 AI 助手。
            回答时必须优先基于给定的知识片段，不要编造不存在的内容。
            如果知识片段不足以回答，请明确说明“当前知识库没有足够信息”。
            回答请使用简体中文，尽量简洁。
            """;

    private final KnowledgeRetrievalService retrievalService;
    private final ChatMemoryService chatMemoryService;
    private final SimpleDashScopeClient dashScopeClient;

    @Override
    public AiChatResponseVO chat(String sessionId, String message) {
        List<AiKnowledgeSearchHitVO> references = retrievalService.retrieve(message, null);
        String context = references.stream()
                .map(hit -> "[知识片段] 标题: " + hit.getTitle() + "\n内容: " + hit.getContentPreview())
                .collect(Collectors.joining("\n\n"));
        String prompt = "请基于以下知识片段回答用户问题。\n\n" + context + "\n\n用户问题: " + message;
        String answer = dashScopeClient.chat(SYSTEM_PROMPT, chatMemoryService.getMessages(sessionId), prompt);
        chatMemoryService.appendRound(sessionId, message, answer);
        AiChatResponseVO response = new AiChatResponseVO();
        response.setAnswer(answer);
        response.setReferences(references);
        return response;
    }
}
