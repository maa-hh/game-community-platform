package com.game.community.ai.service;

import com.game.community.model.base.PageResult;
import com.game.community.model.dto.aiagent.AddKnowledgeTextRequest;
import com.game.community.model.dto.aiagent.AiKnowledgePageQuery;
import com.game.community.model.entity.aiagent.AiKnowledgeDocument;
import com.game.community.model.vo.aiagent.AiKnowledgeDetailVO;
import com.game.community.model.vo.aiagent.AiKnowledgeDocumentVO;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface KnowledgeDocumentService {

    AiKnowledgeDocumentVO addText(Long operatorId, AddKnowledgeTextRequest request);

    AiKnowledgeDocumentVO addFile(Long operatorId, MultipartFile file) throws IOException;

    PageResult<AiKnowledgeDocumentVO> page(AiKnowledgePageQuery query);

    AiKnowledgeDetailVO detail(Long documentId);

    void disable(Long operatorId, Long documentId);

    void delete(Long operatorId, Long documentId);

    void reindex(Long operatorId, Long documentId);

    AiKnowledgeDocument getByIdOrThrow(Long documentId);
}
