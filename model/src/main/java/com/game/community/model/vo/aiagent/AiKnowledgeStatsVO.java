package com.game.community.model.vo.aiagent;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiKnowledgeStatsVO implements Serializable {

    private Long documentCount;

    private Long activeDocumentCount;

    private Long segmentCount;
}
