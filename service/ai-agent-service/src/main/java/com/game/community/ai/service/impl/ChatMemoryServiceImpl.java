package com.game.community.ai.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.community.ai.config.AiAgentProperties;
import com.game.community.ai.service.ChatMemoryService;
import com.game.community.common.constant.aiagent.AiAgentConstants;
import com.game.community.model.vo.aiagent.AiChatMessageVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMemoryServiceImpl implements ChatMemoryService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final AiAgentProperties properties;

    @Override
    public List<AiChatMessageVO> getMessages(String sessionId) {
        String json = redisTemplate.opsForValue().get(buildKey(sessionId));
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            log.warn("解析聊天记忆失败: sessionId={}", sessionId, e);
            return new ArrayList<>();
        }
    }

    @Override
    public void appendRound(String sessionId, String userMessage, String assistantMessage) {
        List<AiChatMessageVO> messages = getMessages(sessionId);
        messages.add(new AiChatMessageVO("user", userMessage, LocalDateTime.now()));
        messages.add(new AiChatMessageVO("assistant", assistantMessage, LocalDateTime.now()));
        List<AiChatMessageVO> trimmed = ChatMemoryWindow.trim(messages, properties.getMaxRounds() * 2);
        try {
            redisTemplate.opsForValue().set(buildKey(sessionId), objectMapper.writeValueAsString(trimmed),
                    properties.getMemoryTtlHours(), TimeUnit.HOURS);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("保存聊天记忆失败", e);
        }
    }

    @Override
    public void clear(String sessionId) {
        redisTemplate.delete(buildKey(sessionId));
    }

    private String buildKey(String sessionId) {
        return AiAgentConstants.CHAT_MEMORY_KEY_PREFIX + sessionId;
    }
}
