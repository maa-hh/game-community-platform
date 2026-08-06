package com.game.community.search.service.impl;

import com.game.community.model.elasticsearch.ArticleDocument;
import com.game.community.search.ai.ArticleEmbeddingTextBuilder;
import com.game.community.search.ai.DashScopeClient;
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

    private final DashScopeClient dashScopeClient;
    private final SearchAiProperties searchAiProperties;

    @Override
    public void enrichEmbedding(ArticleDocument document) {
        if (document == null || !searchAiProperties.isEnabled() || !dashScopeClient.isConfigured()) {
            return;
        }
        try {
            String text = ArticleEmbeddingTextBuilder.build(document);
            List<Float> vector = dashScopeClient.embedding(text);
            if (!CollectionUtils.isEmpty(vector)) {
                document.setEmbedding(vector);
            }
        } catch (Exception e) {
            log.warn("文章 embedding 生成失败: articleId={}, reason={}", document.getId(), e.getMessage());
        }
    }
}
