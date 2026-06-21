package com.game.community.ai.service;

import com.game.community.model.entity.aiagent.AiKnowledgeDocument;
import com.game.community.model.vo.aiagent.AiKnowledgeStatsVO;

public interface KnowledgeIndexService {

    int indexDocument(AiKnowledgeDocument document, String content);

    void deleteDocumentSegments(Long documentId);

    AiKnowledgeStatsVO stats();
}
