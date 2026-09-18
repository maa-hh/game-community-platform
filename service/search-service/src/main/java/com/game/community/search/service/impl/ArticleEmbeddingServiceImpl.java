package com.game.community.search.service.impl;

import com.game.community.model.dto.aiagent.AiEmbeddingRequest;
import com.game.community.model.elasticsearch.ArticleDocument;
import com.game.community.model.message.AiTaskRequestMessage;
import com.game.community.model.message.SearchAiContext;
import com.game.community.search.ai.ArticleEmbeddingTextBuilder;
import com.game.community.search.config.SearchAiProperties;
import com.game.community.search.event.AiTaskProducer;
import com.game.community.search.service.ArticleEmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 文章索引先落库，向量在 Kafka 中异步生成并回写，避免阻塞索引同步。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleEmbeddingServiceImpl implements ArticleEmbeddingService {

    private final AiTaskProducer aiTaskProducer;
    private final SearchAiProperties searchAiProperties;

    @Override
    public void dispatchEmbedding(ArticleDocument document) {
        if (document == null || document.getId() == null || !searchAiProperties.isEnabled()) {
            return;
        }
        AiEmbeddingRequest request = new AiEmbeddingRequest();
        request.setText(ArticleEmbeddingTextBuilder.build(document));
        request.setProvider(searchAiProperties.getEmbeddingProvider());
        SearchAiContext context = new SearchAiContext();
        context.setOperation(SearchAiContext.ARTICLE_EMBEDDING);
        context.setDocument(document);
        aiTaskProducer.send(AiTaskRequestMessage.EMBEDDING, document.getId(), request, context);
        log.debug("文章 embedding 任务已投递: articleId={}", document.getId());
    }
}
