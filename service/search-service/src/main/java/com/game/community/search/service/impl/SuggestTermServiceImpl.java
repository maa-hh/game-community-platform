package com.game.community.search.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.game.community.common.constant.search.SearchConstants;
import com.game.community.model.elasticsearch.SuggestDocument;
import com.game.community.model.entity.search.SuggestTerm;
import com.game.community.model.entity.search.SuggestTermSource;
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
        suggestTermSourceMapper.upsert(existing.getId(), seed.sourceType(), sourceArticleId);
        syncToEs(existing);
        return existing;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void replaceArticleTerms(Long articleId, Collection<TermSeed> seeds) {
        replaceSourceTerms(articleId, List.of(
                SearchConstants.SUGGEST_SOURCE_ARTICLE), seeds);
    }

    /** 替换游戏名称来源的建议词，并清理已不存在的旧游戏词。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void replaceGameTerms(Long appId, Collection<TermSeed> seeds) {
        replaceSourceTerms(appId, List.of(SearchConstants.SUGGEST_SOURCE_GAME), seeds);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void expireArticleSourceTypes(Long articleId, Collection<String> sourceTypes) {
        if (articleId == null || CollectionUtils.isEmpty(sourceTypes)) {
            return;
        }
        List<Long> termIds = suggestTermSourceMapper.selectTermIdsBySourceAndTypes(articleId, sourceTypes);
        suggestTermSourceMapper.deleteBySourceAndTypes(articleId, sourceTypes);
        expireOrphanTerms(termIds);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void expireByArticleId(Long articleId) {
        expireArticleSourceTypes(articleId, List.of(
                SearchConstants.SUGGEST_SOURCE_ARTICLE,
                SearchConstants.SUGGEST_SOURCE_AI));
    }

    /** 删除某个游戏的建议词来源并清理孤立词。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void expireGameTerms(Long appId) {
        if (appId == null) {
            return;
        }
        expireArticleSourceTypes(appId, List.of(SearchConstants.SUGGEST_SOURCE_GAME));
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
        LocalDateTime aiColdBefore = now.minusDays(SearchConstants.SUGGEST_AI_COLD_TTL_DAYS);

        int aiExpired = suggestTermMapper.expireColdTerms(
                SearchConstants.SUGGEST_SOURCE_AI, observeBefore, aiColdBefore, now);

        List<SuggestTerm> expiredRows = suggestTermMapper.selectList(new LambdaQueryWrapper<SuggestTerm>()
                .eq(SuggestTerm::getStatus, SearchConstants.SUGGEST_STATUS_EXPIRED)
                .ge(SuggestTerm::getUpdatedAt, now.minusDays(1)));
        for (SuggestTerm row : expiredRows) {
            elasticsearchService.deleteSuggestion(row.getId());
        }
        log.info("建议词清理完成: aiExpired={}", aiExpired);
        return aiExpired;
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
        document.setSourceTypes(suggestTermSourceMapper.selectSourceTypesByTermId(row.getId()));
        elasticsearchService.indexSuggestion(document);
    }

    private void expireOrphanTerms(Collection<Long> termIds) {
        if (CollectionUtils.isEmpty(termIds)) {
            return;
        }
        for (Long termId : termIds.stream().filter(Objects::nonNull).distinct().toList()) {
            if (suggestTermSourceMapper.countByTermId(termId) > 0) {
                refreshAggregateSource(termId);
                continue;
            }
            SuggestTerm row = suggestTermMapper.selectById(termId);
            expireRow(row);
        }
    }

    /** 来源关系变更后同步主表聚合来源，保证 ES 展示字段与 sourceTypes 一致。 */
    private void refreshAggregateSource(Long termId) {
        SuggestTermSource primary = suggestTermSourceMapper.selectPrimarySourceByTermId(termId);
        SuggestTerm row = suggestTermMapper.selectById(termId);
        if (primary == null || row == null) {
            return;
        }
        row.setSourceType(primary.getSourceType());
        row.setSourceArticleId(primary.getSourceArticleId());
        row.setUpdatedAt(LocalDateTime.now());
        suggestTermMapper.updateById(row);
        syncToEs(row);
    }

    /** 替换同一业务实体下指定来源类型的建议词关系。 */
    private void replaceSourceTerms(Long sourceId, Collection<String> sourceTypes, Collection<TermSeed> seeds) {
        if (sourceId == null) {
            return;
        }
        List<Long> oldTermIds = suggestTermSourceMapper.selectTermIdsBySourceAndTypes(sourceId, sourceTypes);
        suggestTermSourceMapper.deleteBySourceAndTypes(sourceId, sourceTypes);
        expireOrphanTerms(oldTermIds);
        if (CollectionUtils.isEmpty(seeds)) {
            return;
        }
        Map<String, TermSeed> dedup = new LinkedHashMap<>();
        for (TermSeed seed : seeds) {
            if (seed == null) {
                continue;
            }
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
}
