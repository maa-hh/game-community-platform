package com.game.community.search.service;

import com.game.community.model.entity.search.SuggestTerm;

import java.util.Collection;
import java.util.List;

public interface SuggestTermService {

    record TermSeed(String term, String sourceType, Long sourceArticleId, int weight, boolean pinned) {
    }

    SuggestTerm upsertActive(TermSeed seed);

    void replaceArticleTerms(Long articleId, Collection<TermSeed> seeds);

    /** 替换某个游戏的建议词来源，保证游戏改名或类型变化后旧词不再被召回。 */
    void replaceGameTerms(Long appId, Collection<TermSeed> seeds);

    void expireArticleSourceTypes(Long articleId, Collection<String> sourceTypes);

    void expireByArticleId(Long articleId);

    /** 删除某个游戏产生的建议词来源，按来源类型隔离文章与游戏 ID。 */
    void expireGameTerms(Long appId);

    void triggerAsync(Long termId, String term);

    void triggerByKeywordAsync(String keyword);

    int cleanupExpiredTerms();

    List<SuggestTerm> listActiveForEsSync(int offset, int limit);

    long countActive();

    SuggestTerm getById(Long id);

    void disableTerm(Long id);
}
