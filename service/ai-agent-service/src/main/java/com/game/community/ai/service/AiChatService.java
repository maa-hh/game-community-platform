package com.game.community.ai.service;

import com.game.community.model.vo.aiagent.AiChatResponseVO;

public interface AiChatService {

    AiChatResponseVO chat(String sessionId, String message);
}
