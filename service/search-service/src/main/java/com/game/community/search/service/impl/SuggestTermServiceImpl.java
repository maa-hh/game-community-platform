package com.game.community.search.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.game.community.common.constant.search.SearchConstants;
import com.game.community.model.elasticsearch.SuggestDocument;
import com.game.community.model.entity.search.SuggestTerm;
import com.game.community.search.mapper.SuggestTermMapper;
import com.game.community.search.mapper.SuggestTermSourceMapper;
import com.game.community.search.service.ElasticsearchService;
import com.game.community.search.service.SuggestTermService;
import com.game.community.search.util.SuggestTermNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class SuggestTermServiceImpl implements SuggestTermService {

    private final SuggestTermMapper suggestTermMapper;
    private final SuggestTermSourceMapper suggestTermSourceMapper;
    private final ElasticsearchService elasticsearchService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SuggestTerm upsertActive(TermSeed seed) {
        if (seed == null) {
            return null;
        }
        String term = SuggestTermNormalizer.normalize(seed.term());
        if (!StringUtils.hasText(term)) {
            return null;
        }
        long sourceArticleId = seed.sourceArticleId() == null ? 0L : seed.sourceArticleId();
        LocalDateTime now = LocalDateTime.now();
        suggestTermMapper.upsert(term, seed.sourceType(), sourceArticleId, seed.weight(), seed.pinned() ? 1 : 0, now);
        SuggestTerm existing = suggestTermMapper.selectOne(new LambdaQueryWrapper<SuggestTerm>()
                .eq(SuggestTerm::getTerm, term)
                .last("LIMIT 1"));
        if (existing == null) {
            return null;
        }
        if (SearchConstants.SUGGEST_STATUS_DISABLED.equals(existing.getStatus())) {
            return existing;
        }
        syncToEs(existing);
        suggestTermSourceMapper.upsert(existing.getId(), seed.sourceType(), sourceArticleId);
        return existing;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void replaceArticleTerms(Long articleId, Collection<TermSeed> seeds) {
        if (articleId == null) {
            return;
        }
        List<String> sourceTypes = List.of(SearchConstants.SUGGEST_SOURCE_ARTICLE,
                SearchConstants.SUGGEST_SOURCE_TOKEN, SearchConstants.SUGGEST_SOURCE_CATEGORY);
        List<Long> oldTermIds = suggestTermSourceMapper.selectTermIdsByArticleAndTypes(articleId, sourceTypes);
        suggestTermSourceMapper.deleteByArticleAndTypes(articleId, sourceTypes);
        expireOrphanTerms(oldTermIds);
        if (CollectionUtils.isEmpty(seeds)) {
            return;
        }
        Map<String, TermSeed> dedup = new LinkedHashMap<>();
        for (TermSeed seed : seeds) {
            String normalized = SuggestTermNormalizer.normalize(seed.term());
            if (!StringUtils.hasText(normalized)) {
                continue;
            }
            TermSeed current = dedup.get(normalized);
            if (current == null || seed.weight() > current.weight()) {
                dedup.put(normalized, seed);
            }
        }
        dedup.values().forEach(this::upsertActive);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void expireArticleSourceTypes(Long articleId, Collection<String> sourceTypes) {
        if (articleId == null || CollectionUtils.isEmpty(sourceTypes)) {
            return;
        }
        List<Long> termIds = suggestTermSourceMapper.selectTermIdsByArticleAndTypes(articleId, sourceTypes);
        suggestTermSourceMapper.deleteByArticleAndTypes(articleId, sourceTypes);
        expireOrphanTerms(termIds);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void expireByArticleId(Long articleId) {
        if (articleId == null) {
            return;
        }
        List<Long> termIds = suggestTermSourceMapper.selectList(new LambdaQueryWrapper<com.game.community.model.entity.search.SuggestTermSource>()
                        .eq(com.game.community.model.entity.search.SuggestTermSource::getSourceArticleId, articleId))
                .stream().map(com.game.community.model.entity.search.SuggestTermSource::getTermId).distinct().toList();
        suggestTermSourceMapper.deleteByArticle(articleId);
        expireOrphanTerms(termIds);
    }

    @Override
    @Async("taskExecutor")
    public void triggerAsync(Long termId, String term) {
        if (termId != null) {
            suggestTermMapper.incrementTrigger(termId, LocalDateTime.now());
            return;
        }
        triggerByKeywordAsync(term);
    }

    @Override
    @Async("taskExecutor")
    public void triggerByKeywordAsync(String keyword) {
        String normalized = SuggestTermNormalizer.normalize(keyword);
        if (!StringUtils.hasText(normalized)) {
            return;
        }
        SuggestTerm row = suggestTermMapper.selectOne(new LambdaQueryWrapper<SuggestTerm>()
                .eq(SuggestTerm::getTerm, normalized)
                .eq(SuggestTerm::getStatus, SearchConstants.SUGGEST_STATUS_ACTIVE)
                .last("LIMIT 1"));
        if (row != null) {
            suggestTermMapper.incrementTrigger(row.getId(), LocalDateTime.now());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int cleanupExpiredTerms() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime observeBefore = now.minusDays(SearchConstants.SUGGEST_OBSERVE_DAYS);
        LocalDateTime tokenColdBefore = now.minusDays(SearchConstants.SUGGEST_COLD_TTL_DAYS);
        LocalDateTime aiColdBefore = now.minusDays(SearchConstants.SUGGEST_AI_COLD_TTL_DAYS);

        int tokenExpired = suggestTermMapper.expireColdTerms(
                SearchConstants.SUGGEST_SOURCE_TOKEN, observeBefore, tokenColdBefore, now);
        int aiExpired = suggestTermMapper.expireColdTerms(
                SearchConstants.SUGGEST_SOURCE_AI, observeBefore, aiColdBefore, now);

        List<SuggestTerm> expiredRows = suggestTermMapper.selectList(new LambdaQueryWrapper<SuggestTerm>()
                .eq(SuggestTerm::getStatus, SearchConstants.SUGGEST_STATUS_EXPIRED)
                .ge(SuggestTerm::getUpdatedAt, now.minusDays(1)));
        for (SuggestTerm row : expiredRows) {
            elasticsearchService.deleteSuggestion(row.getId());
        }
        log.info("建议词清理完成: tokenExpired={}, aiExpired={}", tokenExpired, aiExpired);
        return tokenExpired + aiExpired;
    }

    @Override
    public List<SuggestTerm> listActiveForEsSync(int offset, int limit) {
        return suggestTermMapper.selectList(new LambdaQueryWrapper<SuggestTerm>()
                .eq(SuggestTerm::getStatus, SearchConstants.SUGGEST_STATUS_ACTIVE)
                .orderByDesc(SuggestTerm::getWeight)
                .orderByAsc(SuggestTerm::getId)
                .last("LIMIT " + offset + "," + limit));
    }

    @Override
    public long countActive() {
        return suggestTermMapper.selectCount(new LambdaQueryWrapper<SuggestTerm>()
                .eq(SuggestTerm::getStatus, SearchConstants.SUGGEST_STATUS_ACTIVE));
    }

    @Override
    public SuggestTerm getById(Long id) {
        return suggestTermMapper.selectById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void disableTerm(Long id) {
        if (id == null) {
            return;
        }
        suggestTermMapper.update(null, new LambdaUpdateWrapper<SuggestTerm>()
                .eq(SuggestTerm::getId, id)
                .set(SuggestTerm::getStatus, SearchConstants.SUGGEST_STATUS_DISABLED)
                .set(SuggestTerm::getUpdatedAt, LocalDateTime.now()));
        elasticsearchService.deleteSuggestion(id);
    }

    private void expireRow(SuggestTerm row) {
        if (row == null || row.getId() == null) {
            return;
        }
        if (Objects.equals(row.getPinned(), 1)) {
            return;
        }
        row.setStatus(SearchConstants.SUGGEST_STATUS_EXPIRED);
        row.setUpdatedAt(LocalDateTime.now());
        suggestTermMapper.updateById(row);
        elasticsearchService.deleteSuggestion(row.getId());
    }

    private void syncToEs(SuggestTerm row) {
        if (row == null || row.getId() == null || !SearchConstants.SUGGEST_STATUS_ACTIVE.equals(row.getStatus())) {
            return;
        }
        SuggestDocument document = new SuggestDocument();
        document.setId(row.getId());
        document.setTermId(row.getId());
        document.setSuggest(row.getTerm());
        document.setSuggestNgram(row.getTerm());
        document.setWeight(row.getWeight());
        document.setSourceType(row.getSourceType());
        document.setSourceArticleId(row.getSourceArticleId());
        elasticsearchService.indexSuggestion(document);
    }

    private void expireOrphanTerms(Collection<Long> termIds) {
        if (CollectionUtils.isEmpty(termIds)) {
            return;
        }
        for (Long termId : termIds.stream().filter(Objects::nonNull).distinct().toList()) {
            if (suggestTermSourceMapper.countByTermId(termId) > 0) {
                continue;
            }
            SuggestTerm row = suggestTermMapper.selectById(termId);
            expireRow(row);
        }
    }
}
