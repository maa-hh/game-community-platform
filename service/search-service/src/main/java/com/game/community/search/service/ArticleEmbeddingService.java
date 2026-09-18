package com.game.community.search.service;

import com.game.community.model.elasticsearch.ArticleDocument;

public interface ArticleEmbeddingService {

    void dispatchEmbedding(ArticleDocument document);
}
