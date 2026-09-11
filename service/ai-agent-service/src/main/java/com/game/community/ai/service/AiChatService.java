package com.game.community.ai.service;

import com.game.community.model.vo.aiagent.AiChatResponseVO;

public interface AiChatService {

    /** 使用指定或默认 provider 完成一次带知识检索的对话。 */
    AiChatResponseVO chat(String sessionId, String message, String provider);
}
