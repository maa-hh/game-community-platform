package com.game.community.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.game.community.ai.mapper.KnowledgeDocumentMapper;
import com.game.community.ai.service.KnowledgeDocumentService;
import com.game.community.ai.service.KnowledgeIndexService;
import com.game.community.common.constant.aiagent.AiAgentConstants;
import com.game.community.model.base.PageResult;
import com.game.community.model.dto.aiagent.AddKnowledgeTextRequest;
import com.game.community.model.dto.aiagent.AiKnowledgePageQuery;
import com.game.community.model.entity.aiagent.AiKnowledgeDocument;
import com.game.community.model.vo.aiagent.AiKnowledgeDetailVO;
import com.game.community.model.vo.aiagent.AiKnowledgeDocumentVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class KnowledgeDocumentServiceImpl implements KnowledgeDocumentService {

    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeIndexService knowledgeIndexService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiKnowledgeDocumentVO addText(Long operatorId, AddKnowledgeTextRequest request) {
        return saveDocument(operatorId, request.getTitle(), request.getSourceName(), request.getContent(),
                AiAgentConstants.SOURCE_TYPE_TEXT);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiKnowledgeDocumentVO addFile(Long operatorId, MultipartFile file) throws IOException {
        String content = new String(file.getBytes(), StandardCharsets.UTF_8);
        return saveDocument(operatorId, file.getOriginalFilename(), file.getOriginalFilename(), content,
                AiAgentConstants.SOURCE_TYPE_FILE);
    }

    @Override
    public PageResult<AiKnowledgeDocumentVO> page(AiKnowledgePageQuery query) {
        Page<AiKnowledgeDocument> page = documentMapper.selectPage(new Page<>(query.getPage(), query.getSize()),
                new LambdaQueryWrapper<AiKnowledgeDocument>()
                        .like(query.getKeyword() != null && !query.getKeyword().isBlank(), AiKnowledgeDocument::getTitle, query.getKeyword())
                        .eq(query.getStatus() != null, AiKnowledgeDocument::getStatus, query.getStatus())
                        .orderByDesc(AiKnowledgeDocument::getId));
        List<AiKnowledgeDocumentVO> records = page.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(records, page.getCurrent(), page.getSize(), page.getTotal());
    }

    @Override
    public AiKnowledgeDetailVO detail(Long documentId) {
        AiKnowledgeDocument document = getByIdOrThrow(documentId);
        AiKnowledgeDetailVO vo = new AiKnowledgeDetailVO();
        vo.setId(document.getId());
        vo.setTitle(document.getTitle());
        vo.setSourceType(document.getSourceType());
        vo.setSourceName(document.getSourceName());
        vo.setContentHash(document.getContentHash());
        vo.setStatus(document.getStatus());
        vo.setIndexStatus(document.getIndexStatus());
        vo.setSegmentCount(document.getSegmentCount());
        vo.setCreatedBy(document.getCreatedBy());
        vo.setUpdatedBy(document.getUpdatedBy());
        vo.setCreateTime(document.getCreateTime());
        vo.setUpdateTime(document.getUpdateTime());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void disable(Long operatorId, Long documentId) {
        AiKnowledgeDocument document = getByIdOrThrow(documentId);
        document.setStatus(AiAgentConstants.DOCUMENT_STATUS_DISABLED);
        document.setUpdatedBy(operatorId);
        documentMapper.updateById(document);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long operatorId, Long documentId) {
        AiKnowledgeDocument document = getByIdOrThrow(documentId);
        knowledgeIndexService.deleteDocumentSegments(documentId);
        documentMapper.deleteById(document.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reindex(Long operatorId, Long documentId) {
        AiKnowledgeDocument document = getByIdOrThrow(documentId);
        if (document.getIndexStatus() != null && document.getIndexStatus() == AiAgentConstants.INDEX_STATUS_INDEXING) {
            throw new IllegalStateException("文档正在重建索引，请稍后再试");
        }
        document.setIndexStatus(AiAgentConstants.INDEX_STATUS_INDEXING);
        document.setUpdatedBy(operatorId);
        documentMapper.updateById(document);
    }

    @Override
    public AiKnowledgeDocument getByIdOrThrow(Long documentId) {
        AiKnowledgeDocument document = documentMapper.selectById(documentId);
        if (document == null) {
            throw new IllegalArgumentException("知识文档不存在");
        }
        return document;
    }

    private AiKnowledgeDocumentVO saveDocument(Long operatorId, String title, String sourceName, String content, Integer sourceType) {
        String contentHash = DigestUtils.md5DigestAsHex(content.getBytes(StandardCharsets.UTF_8));
        long exists = documentMapper.selectCount(new LambdaQueryWrapper<AiKnowledgeDocument>()
                .eq(AiKnowledgeDocument::getContentHash, contentHash));
        if (exists > 0) {
            throw new IllegalStateException("已存在相同内容的知识文档");
        }
        AiKnowledgeDocument document = new AiKnowledgeDocument();
        document.setTitle(title);
        document.setSourceType(sourceType);
        document.setSourceName(sourceName);
        document.setContentHash(contentHash);
        document.setStatus(AiAgentConstants.DOCUMENT_STATUS_ACTIVE);
        document.setIndexStatus(AiAgentConstants.INDEX_STATUS_INDEXING);
        document.setSegmentCount(0);
        document.setCreatedBy(operatorId);
        document.setUpdatedBy(operatorId);
        document.setCreateTime(LocalDateTime.now());
        document.setUpdateTime(LocalDateTime.now());
        documentMapper.insert(document);
        int segmentCount = knowledgeIndexService.indexDocument(document, content);
        document.setSegmentCount(segmentCount);
        document.setIndexStatus(AiAgentConstants.INDEX_STATUS_READY);
        document.setUpdateTime(LocalDateTime.now());
        documentMapper.updateById(document);
        return toVO(document);
    }

    private AiKnowledgeDocumentVO toVO(AiKnowledgeDocument document) {
        AiKnowledgeDocumentVO vo = new AiKnowledgeDocumentVO();
        vo.setId(document.getId());
        vo.setTitle(document.getTitle());
        vo.setSourceType(document.getSourceType());
        vo.setSourceName(document.getSourceName());
        vo.setStatus(document.getStatus());
        vo.setIndexStatus(document.getIndexStatus());
        vo.setSegmentCount(document.getSegmentCount());
        vo.setCreateTime(document.getCreateTime());
        vo.setUpdateTime(document.getUpdateTime());
        return vo;
    }
}
