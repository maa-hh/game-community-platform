package com.game.community.search.service;

import com.game.community.model.entity.search.SuggestTerm;

import java.util.Collection;
import java.util.List;

public interface SuggestTermService {

    record TermSeed(String term, String sourceType, Long sourceArticleId, int weight, boolean pinned) {
    }

    SuggestTerm upsertActive(TermSeed seed);

    void replaceArticleTerms(Long articleId, Collection<TermSeed> seeds);

    void expireArticleSourceTypes(Long articleId, Collection<String> sourceTypes);

    void expireByArticleId(Long articleId);

    void triggerAsync(Long termId, String term);

    void triggerByKeywordAsync(String keyword);

    int cleanupExpiredTerms();

    List<SuggestTerm> listActiveForEsSync(int offset, int limit);

    long countActive();

    SuggestTerm getById(Long id);

    void disableTerm(Long id);
}
