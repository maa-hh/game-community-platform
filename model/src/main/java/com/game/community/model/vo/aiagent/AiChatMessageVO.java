package com.game.community.model.vo.aiagent;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiChatMessageVO implements Serializable {

    private String role;

    private String content;

    private LocalDateTime time;
}
