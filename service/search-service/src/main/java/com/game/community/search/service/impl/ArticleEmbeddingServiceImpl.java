package com.game.community.search.service.impl;

import com.game.community.feign.AiAgentFeignClient;
import com.game.community.model.base.Result;
import com.game.community.model.dto.aiagent.AiEmbeddingRequest;
import com.game.community.model.elasticsearch.ArticleDocument;
import com.game.community.model.vo.aiagent.AiEmbeddingResponseVO;
import com.game.community.search.ai.ArticleEmbeddingTextBuilder;
import com.game.community.search.config.SearchAiProperties;
import com.game.community.search.service.ArticleEmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleEmbeddingServiceImpl implements ArticleEmbeddingService {

    private final AiAgentFeignClient aiAgentFeignClient;
    private final SearchAiProperties searchAiProperties;

    @Override
    public void enrichEmbedding(ArticleDocument document) {
        if (document == null || !searchAiProperties.isEnabled()) {
            return;
        }
        try {
            String text = ArticleEmbeddingTextBuilder.build(document);
            AiEmbeddingRequest request = new AiEmbeddingRequest();
            request.setText(text);
            request.setProvider(searchAiProperties.getEmbeddingProvider());
            Result<AiEmbeddingResponseVO> result = aiAgentFeignClient.embedding(request);
            List<Float> vector = result != null && Integer.valueOf(200).equals(result.getCode())
                    && result.getData() != null ? result.getData().getVector() : List.of();
            if (!CollectionUtils.isEmpty(vector)) {
                document.setEmbedding(vector);
            }
        } catch (Exception e) {
            log.warn("文章 embedding 生成失败: articleId={}, reason={}", document.getId(), e.getMessage());
        }
    }
}
