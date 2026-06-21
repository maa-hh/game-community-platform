package com.game.community.model.vo.aiagent;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class AiChatResponseVO implements Serializable {

    private String answer;

    private List<AiKnowledgeSearchHitVO> references;
}
