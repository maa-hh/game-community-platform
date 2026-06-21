package com.game.community.search.service;

public interface ArticleSyncService {

    void syncArticle(Long articleId);

    void deleteArticle(Long articleId);

    void rebuildPublishedArticles();

    void rebuildArticleSuggestions();
}
