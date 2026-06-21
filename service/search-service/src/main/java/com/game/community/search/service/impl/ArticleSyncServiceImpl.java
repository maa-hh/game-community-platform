package com.game.community.search.service.impl;

import com.game.community.common.constant.content.ContentConstants;
import com.game.community.feign.ContentFeignClient;
import com.game.community.feign.UserFeignClient;
import com.game.community.model.base.PageResult;
import com.game.community.model.base.Result;
import com.game.community.model.elasticsearch.ArticleDocument;
import com.game.community.model.elasticsearch.SuggestDocument;
import com.game.community.model.entity.article.Article;
import com.game.community.model.entity.article.Category;
import com.game.community.model.vo.article.ArticleDetailVO;
import com.game.community.model.vo.user.UserVO;
import com.game.community.search.service.ArticleSyncService;
import com.game.community.search.service.ElasticsearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleSyncServiceImpl implements ArticleSyncService {

    private static final int REBUILD_PAGE_SIZE = 100;

    private final ContentFeignClient contentFeignClient;
    private final UserFeignClient userFeignClient;
    private final ElasticsearchService elasticsearchService;

    @Override
    public void syncArticle(Long articleId) {
        if (articleId == null) {
            return;
        }
        ArticleDetailVO detail = Optional.ofNullable(contentFeignClient.getArticleDetail(articleId))
                .map(Result::getData)
                .orElse(null);
        if (detail == null || !java.util.Objects.equals(detail.getStatus(), ContentConstants.ArticleStatus.PUBLISHED)) {
            elasticsearchService.deleteArticle(articleId);
            return;
        }
        ArticleDocument document = toDocument(detail);
        elasticsearchService.indexArticle(document);
        indexSuggestionsFromArticle(document);
    }

    @Override
    public void deleteArticle(Long articleId) {
        elasticsearchService.deleteArticle(articleId);
    }

    @Override
    public void rebuildPublishedArticles() {
        int page = 1;
        while (true) {
            PageResult<Article> pageResult = Optional.ofNullable(contentFeignClient.listPublishedArticlesPage(page, REBUILD_PAGE_SIZE))
                    .map(Result::getData)
                    .orElse(null);
            if (pageResult == null || pageResult.getData() == null || pageResult.getData().isEmpty()) {
                break;
            }
            for (Article article : pageResult.getData()) {
                syncArticle(article.getId());
            }
            if ((long) page * REBUILD_PAGE_SIZE >= pageResult.getTotal()) {
                break;
            }
            page++;
        }
    }

    @Override
    public void rebuildArticleSuggestions() {
        Set<String> suggestions = new LinkedHashSet<>();
        loadEnabledCategoryNames().forEach(suggestions::add);

        int page = 1;
        while (true) {
            PageResult<Article> pageResult = Optional.ofNullable(contentFeignClient.listPublishedArticlesPage(page, REBUILD_PAGE_SIZE))
                    .map(Result::getData)
                    .orElse(null);
            if (pageResult == null || pageResult.getData() == null || pageResult.getData().isEmpty()) {
                break;
            }
            for (Article article : pageResult.getData()) {
                addIfText(suggestions, article.getTitle());
                addIfText(suggestions, article.getSummary());
            }
            if ((long) page * REBUILD_PAGE_SIZE >= pageResult.getTotal()) {
                break;
            }
            page++;
        }

        List<SuggestDocument> documents = suggestions.stream()
                .map(this::toSuggestDocument)
                .toList();
        elasticsearchService.batchAddSuggestions(documents);
        log.info("文章建议词重建完成: count={}", documents.size());
    }

    private ArticleDocument toDocument(ArticleDetailVO detail) {
        ArticleDocument document = new ArticleDocument();
        document.setId(detail.getId());
        document.setUserId(detail.getUserId());
        document.setTitle(detail.getTitle());
        document.setSummary(detail.getSummary());
        document.setContent(detail.getContent());
        document.setCoverUrl(detail.getCoverUrl());
        document.setCategoryId(detail.getCategoryId());
        document.setStatus(detail.getStatus());
        document.setPublishedTime(detail.getPublishedTime());
        document.setCreateTime(detail.getCreateTime());
        document.setUpdateTime(detail.getUpdateTime());
        enrichAuthor(document);
        enrichCategory(document);
        return document;
    }

    private void enrichAuthor(ArticleDocument document) {
        if (document.getUserId() == null) {
            return;
        }
        try {
            List<UserVO> users = Optional.ofNullable(userFeignClient.getUsersByIds(List.of(document.getUserId())))
                    .map(Result::getData)
                    .orElse(List.of());
            Map<Long, UserVO> userMap = users.stream().collect(Collectors.toMap(UserVO::getId, item -> item, (a, b) -> a));
            UserVO user = userMap.get(document.getUserId());
            if (user != null) {
                document.setUsername(user.getUsername());
                document.setAvatar(user.getAvatar());
            }
        } catch (Exception e) {
            log.warn("搜索索引同步获取作者失败: userId={}", document.getUserId(), e);
        }
    }

    private void enrichCategory(ArticleDocument document) {
        if (document.getCategoryId() == null) {
            return;
        }
        try {
            Category category = Optional.ofNullable(contentFeignClient.getCategoryById(document.getCategoryId()))
                    .map(Result::getData)
                    .orElse(null);
            if (category != null && StringUtils.hasText(category.getName())) {
                document.setCategoryName(category.getName());
            }
        } catch (Exception e) {
            log.warn("搜索索引同步获取分类失败: categoryId={}", document.getCategoryId(), e);
        }
    }

    private void indexSuggestionsFromArticle(ArticleDocument document) {
        Set<String> suggestions = new LinkedHashSet<>();
        addIfText(suggestions, document.getTitle());
        addIfText(suggestions, document.getSummary());
        addIfText(suggestions, document.getCategoryName());
        List<SuggestDocument> documents = suggestions.stream()
                .map(this::toSuggestDocument)
                .toList();
        elasticsearchService.batchAddSuggestions(documents);
    }

    private List<String> loadEnabledCategoryNames() {
        try {
            return Optional.ofNullable(contentFeignClient.listEnabledCategories())
                    .map(Result::getData)
                    .orElse(List.of())
                    .stream()
                    .map(Category::getName)
                    .filter(StringUtils::hasText)
                    .distinct()
                    .toList();
        } catch (Exception e) {
            log.warn("搜索建议词加载分类失败: {}", e.getMessage(), e);
            return List.of();
        }
    }

    private void addIfText(Set<String> suggestions, String text) {
        if (StringUtils.hasText(text)) {
            suggestions.add(text.trim());
        }
    }

    private SuggestDocument toSuggestDocument(String suggest) {
        SuggestDocument document = new SuggestDocument();
        document.setId(suggestId(suggest));
        document.setSuggest(suggest);
        document.setSuggestNgram(suggest);
        return document;
    }

    private Long suggestId(String suggest) {
        CRC32 crc32 = new CRC32();
        crc32.update(suggest.getBytes(StandardCharsets.UTF_8));
        return crc32.getValue();
    }
}
