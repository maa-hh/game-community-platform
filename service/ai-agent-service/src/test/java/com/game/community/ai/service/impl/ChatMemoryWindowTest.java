package com.game.community.ai.service.impl;

import com.game.community.model.vo.aiagent.AiChatMessageVO;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChatMemoryWindowTest {

    @Test
    void shouldKeepLatestMessagesOnly() {
        List<AiChatMessageVO> messages = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            messages.add(new AiChatMessageVO("user", "m" + i, LocalDateTime.now()));
        }
        List<AiChatMessageVO> trimmed = ChatMemoryWindow.trim(messages, 20);
        assertThat(trimmed).hasSize(20);
        assertThat(trimmed.get(0).getContent()).isEqualTo("m4");
        assertThat(trimmed.get(19).getContent()).isEqualTo("m23");
    }
}
