package com.game.community.search.service;

public interface ArticleSyncService {

    void syncArticle(Long articleId);

    void deleteArticle(Long articleId);

    void rebuildPublishedArticles();

    void rebuildArticleSuggestions();

    /** 全量同步：已发布文章 ES 索引 + 建议词 */
    void rebuildAll();
}
