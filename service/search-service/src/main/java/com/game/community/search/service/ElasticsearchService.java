package com.game.community.search.service;

import com.game.community.model.elasticsearch.ArticleDocument;
import com.game.community.model.elasticsearch.SuggestDocument;

import java.util.List;

public interface ElasticsearchService {

    void indexArticle(ArticleDocument document);

    void deleteArticle(Long articleId);

    void batchAddSuggestions(List<SuggestDocument> documents);

    void deleteSuggestion(Long id);
}
