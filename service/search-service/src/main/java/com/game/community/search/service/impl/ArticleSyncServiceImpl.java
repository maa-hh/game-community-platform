package com.game.community.search.service.impl;

import com.game.community.common.constant.content.ContentConstants;
import com.game.community.common.constant.search.SearchConstants;
import com.game.community.feign.ContentFeignClient;
import com.game.community.feign.UserFeignClient;
import com.game.community.model.base.PageResult;
import com.game.community.model.base.Result;
import com.game.community.model.elasticsearch.ArticleDocument;
import com.game.community.model.vo.article.ArticleDetailVO;
import com.game.community.model.vo.article.ArticleListVO;
import com.game.community.model.vo.article.CategoryVO;
import com.game.community.model.vo.user.UserCardInternalVO;
import com.game.community.model.vo.user.UserCardVO;
import com.game.community.search.service.AiSuggestTermService;
import com.game.community.search.service.ArticleEmbeddingService;
import com.game.community.search.service.ArticleSyncService;
import com.game.community.search.service.ElasticsearchService;
import com.game.community.search.service.SuggestTermService;
import com.game.community.search.service.SuggestTermService.TermSeed;
import com.game.community.search.util.SuggestTermNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleSyncServiceImpl implements ArticleSyncService {

    private final ContentFeignClient contentFeignClient;
    private final UserFeignClient userFeignClient;
    private final ElasticsearchService elasticsearchService;
    private final SuggestTermService suggestTermService;
    private final SuggestTermTokenizer suggestTermTokenizer;
    private final ArticleEmbeddingService articleEmbeddingService;
    private final AiSuggestTermService aiSuggestTermService;

    @Override
    public void syncArticle(Long articleId) {
        if (articleId == null) {
            return;
        }
        ArticleDetailVO detail = Optional.ofNullable(contentFeignClient.getArticleDetail(articleId))
                .map(Result::getData)
                .orElse(null);
        if (detail == null || !Objects.equals(detail.getStatus(), ContentConstants.ArticleStatus.PUBLISHED)) {
            deleteArticle(articleId);
            return;
        }
        ArticleDocument document = toDocument(detail);
        articleEmbeddingService.enrichEmbedding(document);
        elasticsearchService.indexArticle(document);
        suggestTermService.replaceArticleTerms(articleId, buildArticleTermSeeds(document));
        aiSuggestTermService.expandAsync(articleId, document);
    }

    @Override
    public void deleteArticle(Long articleId) {
        if (articleId == null) {
            return;
        }
        elasticsearchService.deleteArticle(articleId);
        suggestTermService.expireByArticleId(articleId);
    }

    @Override
    public void rebuildAll() {
        rebuildPublishedArticles();
        rebuildArticleSuggestions();
    }

    @Override
    public void rebuildPublishedArticles() {
        int page = 1;
        while (true) {
            PageResult<ArticleListVO> pageResult = Optional.ofNullable(contentFeignClient.listPublishedArticlesPage(page, SearchConstants.ARTICLE_REBUILD_PAGE_SIZE))
                    .map(Result::getData)
                    .orElse(null);
            if (pageResult == null || pageResult.getData() == null || pageResult.getData().isEmpty()) {
                break;
            }
            for (ArticleListVO article : pageResult.getData()) {
                syncArticle(article.getId());
            }
            if ((long) page * SearchConstants.ARTICLE_REBUILD_PAGE_SIZE >= pageResult.getTotal()) {
                break;
            }
            page++;
        }
    }

    @Override
    public void rebuildArticleSuggestions() {
        loadEnabledCategoryNames().forEach(name ->
                suggestTermService.upsertActive(new TermSeed(
                        name,
                        SearchConstants.SUGGEST_SOURCE_CATEGORY,
                        null,
                        SearchConstants.WEIGHT_CATEGORY,
                        false)));

        int offset = 0;
            int pageSize = SearchConstants.SUGGEST_ES_SYNC_PAGE_SIZE;
        int synced = 0;
        while (true) {
            List<com.game.community.model.entity.search.SuggestTerm> rows =
                    suggestTermService.listActiveForEsSync(offset, pageSize);
            if (rows.isEmpty()) {
                break;
            }
            for (com.game.community.model.entity.search.SuggestTerm row : rows) {
                suggestTermService.upsertActive(new TermSeed(
                        row.getTerm(),
                        row.getSourceType(),
                        row.getSourceArticleId(),
                        row.getWeight() == null ? SearchConstants.WEIGHT_TOKEN : row.getWeight(),
                        row.getPinned() != null && row.getPinned() == 1));
                synced++;
            }
            if (rows.size() < pageSize) {
                break;
            }
            offset += pageSize;
        }
        log.info("建议词重建完成: activeCount={}, synced={}", suggestTermService.countActive(), synced);
    }

    private ArticleDocument toDocument(ArticleDetailVO detail) {
        ArticleDocument document = new ArticleDocument();
        document.setId(detail.getId());
        document.setPublicId(detail.getPublicId());
        document.setAuthorAccountId(detail.getAuthorAccountId());
        document.setTitle(detail.getTitle());
        document.setSummary(detail.getSummary());
        document.setContent(detail.getContent());
        document.setCoverUrl(detail.getCoverUrl());
        document.setPostType(detail.getPostType());
        document.setRefArticleId(detail.getRefArticleId());
        document.setRefArticle(detail.getRefArticle());
        document.setVideoUrl(detail.getVideoUrl());
        document.setCategoryId(detail.getCategoryId());
        document.setCategoryIds(resolveCategoryIds(detail));
        document.setCategoryNames(resolveCategoryNames(detail));
        document.setGameTags(detail.getGameTags());
        document.setCategoryName(firstCategoryName(document.getCategoryNames(), detail.getCategoryId()));
        document.setStatus(detail.getStatus());
        document.setPublishedTime(detail.getPublishedTime());
        document.setCreateTime(detail.getCreateTime());
        document.setUpdateTime(detail.getUpdateTime());
        enrichAuthor(document);
        if (CollectionUtils.isEmpty(document.getCategoryNames()) && document.getCategoryId() != null) {
            enrichCategory(document);
        }
        return document;
    }

    private List<Long> resolveCategoryIds(ArticleDetailVO detail) {
        if (!CollectionUtils.isEmpty(detail.getCategoryIds())) {
            return detail.getCategoryIds();
        }
        if (detail.getCategoryId() != null) {
            return List.of(detail.getCategoryId());
        }
        return List.of();
    }

    private List<String> resolveCategoryNames(ArticleDetailVO detail) {
        if (!CollectionUtils.isEmpty(detail.getCategoryNames())) {
            return detail.getCategoryNames();
        }
        return List.of();
    }

    private String firstCategoryName(List<String> names, Long categoryId) {
        if (!CollectionUtils.isEmpty(names)) {
            return names.get(0);
        }
        return categoryId == null ? null : String.valueOf(categoryId);
    }

    private List<TermSeed> buildArticleTermSeeds(ArticleDocument document) {
        List<TermSeed> seeds = new ArrayList<>();
        Long articleId = document.getId();
        addTitleSeeds(seeds, articleId, document.getTitle());
        addTitleSeeds(seeds, articleId, document.getSummary());
        if (!CollectionUtils.isEmpty(document.getCategoryNames())) {
            for (String categoryName : document.getCategoryNames()) {
                seeds.add(new TermSeed(categoryName, SearchConstants.SUGGEST_SOURCE_CATEGORY, articleId, SearchConstants.WEIGHT_CATEGORY, false));
            }
        } else if (StringUtils.hasText(document.getCategoryName())) {
            seeds.add(new TermSeed(document.getCategoryName(), SearchConstants.SUGGEST_SOURCE_CATEGORY, articleId, SearchConstants.WEIGHT_CATEGORY, false));
        }
        return seeds;
    }

    private void addTitleSeeds(List<TermSeed> seeds, Long articleId, String text) {
        String normalized = SuggestTermNormalizer.normalize(text);
        if (!StringUtils.hasText(normalized)) {
            return;
        }
        seeds.add(new TermSeed(normalized, SearchConstants.SUGGEST_SOURCE_ARTICLE, articleId, SearchConstants.WEIGHT_ARTICLE_TITLE, false));
        for (String token : suggestTermTokenizer.tokenize(text)) {
            if (Objects.equals(token, normalized)) {
                continue;
            }
            seeds.add(new TermSeed(token, SearchConstants.SUGGEST_SOURCE_TOKEN, articleId, SearchConstants.WEIGHT_TOKEN, false));
        }
    }

    private void enrichAuthor(ArticleDocument document) {
        if (document.getAuthorAccountId() == null) {
            return;
        }
        try {
            List<UserCardVO> users = Optional.ofNullable(
                            userFeignClient.getUsersByAccountIds(List.of(document.getAuthorAccountId())))
                    .map(Result::getData)
                    .orElse(List.of());
            UserCardVO user = users.isEmpty() ? null : users.get(0);
            if (user != null) {
                document.setUsername(user.getUsername());
                document.setAvatar(user.getAvatar());
            }
        } catch (Exception e) {
            log.warn("搜索索引同步获取作者失败: accountId={}", document.getAuthorAccountId(), e);
        }
    }

    private void enrichCategory(ArticleDocument document) {
        if (document.getCategoryId() == null) {
            return;
        }
        try {
            CategoryVO category = Optional.ofNullable(contentFeignClient.getCategoryById(document.getCategoryId()))
                    .map(Result::getData)
                    .orElse(null);
            if (category != null && StringUtils.hasText(category.getName())) {
                document.setCategoryName(category.getName());
                if (CollectionUtils.isEmpty(document.getCategoryNames())) {
                    document.setCategoryNames(List.of(category.getName()));
                }
            }
        } catch (Exception e) {
            log.warn("搜索索引同步获取分类失败: categoryId={}", document.getCategoryId(), e);
        }
    }

    private List<String> loadEnabledCategoryNames() {
        try {
            return Optional.ofNullable(contentFeignClient.listEnabledCategories())
                    .map(Result::getData)
                    .orElse(List.of())
                    .stream()
                    .map(CategoryVO::getName)
                    .filter(StringUtils::hasText)
                    .distinct()
                    .toList();
        } catch (Exception e) {
            log.warn("搜索建议词加载分类失败: {}", e.getMessage(), e);
            return List.of();
        }
    }
}
