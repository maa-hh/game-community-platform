package com.game.community.ai.service.impl;

import com.game.community.model.vo.aiagent.AiChatMessageVO;

import java.util.ArrayList;
import java.util.List;

final class ChatMemoryWindow {

    private ChatMemoryWindow() {
    }

    static List<AiChatMessageVO> trim(List<AiChatMessageVO> messages, int maxMessages) {
        if (messages == null || messages.size() <= maxMessages) {
            return messages == null ? new ArrayList<>() : new ArrayList<>(messages);
        }
        return new ArrayList<>(messages.subList(messages.size() - maxMessages, messages.size()));
    }
}
