package com.game.community.ai.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.game.community.ai.mapper.KnowledgeDocumentMapper;
import com.game.community.ai.service.KnowledgeIndexService;
import com.game.community.common.constant.aiagent.AiAgentConstants;
import com.game.community.model.base.PageResult;
import com.game.community.model.dto.aiagent.AddKnowledgeTextRequest;
import com.game.community.model.dto.aiagent.AiKnowledgePageQuery;
import com.game.community.model.entity.aiagent.AiKnowledgeDocument;
import com.game.community.model.vo.aiagent.AiKnowledgeDocumentVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class KnowledgeDocumentServiceImplTest {

    private KnowledgeDocumentMapper mapper;
    private KnowledgeIndexService indexService;
    private KnowledgeDocumentServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(KnowledgeDocumentMapper.class);
        indexService = mock(KnowledgeIndexService.class);
        service = new KnowledgeDocumentServiceImpl(mapper, indexService);
    }

    @Test
    void shouldCreateDocumentAndIndexSegments() {
        AddKnowledgeTextRequest request = new AddKnowledgeTextRequest();
        request.setTitle("新手攻略");
        request.setContent("这里是一段攻略内容");
        request.setSourceName("admin");
        when(mapper.selectCount(any())).thenReturn(0L);
        doAnswer(invocation -> {
            AiKnowledgeDocument doc = invocation.getArgument(0);
            doc.setId(99L);
            return 1;
        }).when(mapper).insert(any(AiKnowledgeDocument.class));
        when(indexService.indexDocument(any(), any())).thenReturn(3);

        AiKnowledgeDocumentVO result = service.addText(1L, request);

        assertThat(result.getId()).isEqualTo(99L);
        assertThat(result.getSegmentCount()).isEqualTo(3);
        ArgumentCaptor<AiKnowledgeDocument> captor = ArgumentCaptor.forClass(AiKnowledgeDocument.class);
        verify(mapper, atLeastOnce()).updateById(captor.capture());
        assertThat(captor.getAllValues().get(captor.getAllValues().size() - 1).getIndexStatus())
                .isEqualTo(AiAgentConstants.INDEX_STATUS_READY);
    }

    @Test
    void shouldReturnPagedDocuments() {
        AiKnowledgePageQuery query = new AiKnowledgePageQuery();
        query.setPage(1L);
        query.setSize(10L);
        Page<AiKnowledgeDocument> page = new Page<>(1, 10);
        AiKnowledgeDocument document = new AiKnowledgeDocument();
        document.setId(1L);
        document.setTitle("新手攻略");
        document.setStatus(1);
        document.setIndexStatus(1);
        document.setSegmentCount(2);
        page.setRecords(List.of(document));
        page.setTotal(1L);
        when(mapper.selectPage(any(), any())).thenReturn(page);

        PageResult<AiKnowledgeDocumentVO> result = service.page(query);

        assertThat(result.getData()).hasSize(1);
        assertThat(result.getData().get(0).getTitle()).isEqualTo("新手攻略");
    }
}
