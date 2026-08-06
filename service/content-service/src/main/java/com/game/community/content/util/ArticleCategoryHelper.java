package com.game.community.content.util;

import com.game.community.model.entity.article.Article;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 文章多分类：读写 t_article.category_ids（JSON）+ 主分类 category_id
 */
public final class ArticleCategoryHelper {

    private ArticleCategoryHelper() {
    }

    public static List<Long> resolveIds(Article article) {
        if (article == null) {
            return List.of();
        }
        if (!CollectionUtils.isEmpty(article.getCategoryIds())) {
            return List.copyOf(article.getCategoryIds());
        }
        if (article.getCategoryId() != null && article.getCategoryId() > 0) {
            return List.of(article.getCategoryId());
        }
        return List.of();
    }

    public static List<Long> normalizeIds(List<Long> categoryIds) {
        if (CollectionUtils.isEmpty(categoryIds)) {
            return List.of();
        }
        List<Long> result = new ArrayList<>();
        for (Long categoryId : categoryIds) {
            if (categoryId == null || categoryId <= 0 || result.contains(categoryId)) {
                continue;
            }
            result.add(categoryId);
        }
        return result;
    }

    public static Map<Long, List<Long>> mapIdsByArticles(List<Article> articles) {
        if (CollectionUtils.isEmpty(articles)) {
            return Map.of();
        }
        Map<Long, List<Long>> result = new LinkedHashMap<>();
        for (Article article : articles) {
            if (article.getId() == null) {
                continue;
            }
            result.put(article.getId(), resolveIds(article));
        }
        return result;
    }

    public static List<Long> resolveIds(Long articleId, Long primaryCategoryId, Map<Long, List<Long>> categoryMap) {
        List<Long> ids = categoryMap.getOrDefault(articleId, Collections.emptyList());
        if (!ids.isEmpty()) {
            return ids;
        }
        if (primaryCategoryId != null && primaryCategoryId > 0) {
            return List.of(primaryCategoryId);
        }
        return List.of();
    }
}
