package com.game.community.ai.service;

import com.game.community.model.vo.aiagent.AiKnowledgeDebugVO;
import com.game.community.model.vo.aiagent.AiKnowledgeSearchHitVO;

import java.util.List;

public interface KnowledgeRetrievalService {

    List<AiKnowledgeSearchHitVO> retrieve(String query, Integer topK);

    AiKnowledgeDebugVO debugSearch(String query, Integer topK);
}
