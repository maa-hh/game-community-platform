package com.game.community.search.service;

import com.game.community.model.elasticsearch.ArticleDocument;
import com.game.community.model.elasticsearch.GameIndexDocument;
import com.game.community.model.elasticsearch.SuggestDocument;
import com.game.community.model.vo.game.GameListItemVO;

import java.util.List;
import java.util.Set;
import java.time.LocalDateTime;

public interface ElasticsearchService {

    void indexArticle(ArticleDocument document);

    void updateArticleEmbedding(Long articleId, List<Float> vector);

    void deleteArticle(Long articleId);

    void deleteArticlesNotIn(Set<Long> articleIds);

    void indexGame(GameIndexDocument document);

    void indexGame(GameListItemVO game);

    void indexGame(GameListItemVO game, LocalDateTime eventTime);

    void deleteGame(Long appId);

    void deleteGamesNotIn(Set<Long> appIds);

    void batchAddSuggestions(List<SuggestDocument> documents);

    void clearSuggestions();

    void indexSuggestion(SuggestDocument document);

    void deleteSuggestion(Long id);
}
