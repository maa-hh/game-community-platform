package com.game.community.ai.service.impl;

import com.game.community.ai.agent.AgentModelRegistry;
import com.game.community.ai.service.AiChatService;
import com.game.community.ai.service.ChatMemoryService;
import com.game.community.ai.service.KnowledgeRetrievalService;
import com.game.community.model.vo.aiagent.AiChatResponseVO;
import com.game.community.model.vo.aiagent.AiKnowledgeSearchHitVO;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AiChatServiceImpl implements AiChatService {

    private final KnowledgeRetrievalService retrievalService;
    private final ChatMemoryService chatMemoryService;
    private final AgentModelRegistry modelRegistry;

    @Value("classpath:prompts/chat-system.st")
    private Resource systemPromptResource;

    @Value("classpath:prompts/chat-user.st")
    private Resource userPromptResource;

    @Override
    public AiChatResponseVO chat(String sessionId, String message, String provider) {
        List<AiKnowledgeSearchHitVO> references = retrievalService.retrieve(message, null);
        String context = references.stream()
                .map(hit -> "[知识片段] 标题: " + hit.getTitle() + "\n内容: " + hit.getContentPreview())
                .collect(Collectors.joining("\n\n"));
        String prompt = new PromptTemplate(userPromptResource).render(java.util.Map.of(
                "context", context,
                "question", message
        ));
        List<Message> history = new ArrayList<>();
        chatMemoryService.getMessages(sessionId).forEach(item -> {
            if ("assistant".equalsIgnoreCase(item.getRole())) {
                history.add(new AssistantMessage(item.getContent()));
            } else if ("user".equalsIgnoreCase(item.getRole())) {
                history.add(new UserMessage(item.getContent()));
            }
        });
        String answer = modelRegistry.chat(provider).client().prompt()
                .system(new PromptTemplate(systemPromptResource).render())
                .messages(history)
                .user(prompt)
                .call()
                .content();
        chatMemoryService.appendRound(sessionId, message, answer);
        AiChatResponseVO response = new AiChatResponseVO();
        response.setAnswer(answer);
        response.setReferences(references);
        return response;
    }
}
