package com.game.community.ai.service;

import com.game.community.model.dto.aiagent.AiEmbeddingRequest;
import com.game.community.model.dto.aiagent.AiSearchTermsRequest;
import com.game.community.model.vo.aiagent.AiEmbeddingResponseVO;
import com.game.community.model.vo.aiagent.AiSearchTermsResponseVO;

public interface AiCapabilityService {

    AiEmbeddingResponseVO embedding(AiEmbeddingRequest request);

    AiSearchTermsResponseVO expandSearchTerms(AiSearchTermsRequest request);
}
