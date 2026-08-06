package com.game.community.search.service;

import com.game.community.model.elasticsearch.ArticleDocument;

public interface AiSuggestTermService {

    void expandAsync(Long articleId, ArticleDocument document);
}
