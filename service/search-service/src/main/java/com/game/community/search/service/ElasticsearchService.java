package com.game.community.search.service;

import com.game.community.model.elasticsearch.ArticleDocument;
import com.game.community.model.elasticsearch.GameIndexDocument;
import com.game.community.model.elasticsearch.SuggestDocument;
import com.game.community.model.vo.game.GameListItemVO;

import java.util.List;

public interface ElasticsearchService {

    void indexArticle(ArticleDocument document);

    void deleteArticle(Long articleId);

    void indexGame(GameIndexDocument document);

    void indexGame(GameListItemVO game);

    void deleteGame(Long appId);

    void batchAddSuggestions(List<SuggestDocument> documents);

    void indexSuggestion(SuggestDocument document);

    void deleteSuggestion(Long id);
}
