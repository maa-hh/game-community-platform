package com.game.community.ai.service;

import com.game.community.model.vo.aiagent.AiChatMessageVO;

import java.util.List;

public interface ChatMemoryService {

    List<AiChatMessageVO> getMessages(String sessionId);

    void appendRound(String sessionId, String userMessage, String assistantMessage);

    void clear(String sessionId);
}
