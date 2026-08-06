package com.game.community.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.game.community.content.mapper.ArticleMapper;
import com.game.community.content.mapper.ArticleCategoryMapper;
import com.game.community.content.service.ArticleCategoryService;
import com.game.community.content.service.CategoryService;
import com.game.community.content.util.ArticleCategoryHelper;
import com.game.community.model.entity.article.Article;
import com.game.community.model.entity.article.Category;
import com.game.community.model.entity.article.ArticleCategory;
import com.game.community.model.vo.article.ArticleDetailVO;
import com.game.community.model.vo.article.ArticleListVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ArticleCategoryServiceImpl implements ArticleCategoryService {

    private final ArticleMapper articleMapper;
    private final ArticleCategoryMapper articleCategoryMapper;
    private final CategoryService categoryService;

    @Override
    public List<Long> listCategoryIds(Long articleId) {
        if (articleId == null) {
            return List.of();
        }
        List<Long> relationIds = articleCategoryMapper.selectCategoryIdsByArticleId(articleId);
        if (!CollectionUtils.isEmpty(relationIds)) {
            return relationIds;
        }
        Article article = articleMapper.selectById(articleId);
        return ArticleCategoryHelper.resolveIds(article);
    }

    @Override
    public void replaceCategoryRelations(Long articleId, List<Long> categoryIds) {
        if (articleId == null) {
            return;
        }
        articleCategoryMapper.delete(new LambdaQueryWrapper<ArticleCategory>()
                .eq(ArticleCategory::getArticleId, articleId));
        if (CollectionUtils.isEmpty(categoryIds)) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (Long categoryId : categoryIds.stream().filter(Objects::nonNull).distinct().toList()) {
            ArticleCategory relation = new ArticleCategory();
            relation.setArticleId(articleId);
            relation.setCategoryId(categoryId);
            relation.setCreateTime(now);
            articleCategoryMapper.insert(relation);
        }
    }

    @Override
    public Map<Long, List<Long>> mapCategoryIdsByArticleIds(List<Long> articleIds) {
        if (CollectionUtils.isEmpty(articleIds)) {
            return Map.of();
        }
        List<ArticleCategory> relations = articleCategoryMapper.selectByArticleIds(articleIds);
        Map<Long, List<Long>> relationMap = relations.stream().collect(Collectors.groupingBy(
                ArticleCategory::getArticleId,
                Collectors.mapping(ArticleCategory::getCategoryId, Collectors.toList())));
        List<Article> articles = articleMapper.selectList(new LambdaQueryWrapper<Article>()
                .in(Article::getId, articleIds)
                .select(Article::getId, Article::getCategoryId, Article::getCategoryIds));
        Map<Long, List<Long>> result = ArticleCategoryHelper.mapIdsByArticles(articles);
        relationMap.forEach(result::put);
        return result;
    }

    @Override
    public void enrichListVOs(List<ArticleListVO> articles) {
        if (CollectionUtils.isEmpty(articles)) {
            return;
        }
        List<Long> articleIds = articles.stream().map(ArticleListVO::getId).filter(Objects::nonNull).toList();
        Map<Long, List<Long>> categoryMap = mapCategoryIdsByArticleIds(articleIds);
        Map<Long, String> nameMap = categoryNameMap();
        for (ArticleListVO article : articles) {
            List<Long> ids = ArticleCategoryHelper.resolveIds(article.getId(), article.getCategoryId(), categoryMap);
            article.setCategoryIds(ids);
            article.setCategoryNames(ids.stream().map(nameMap::get).filter(Objects::nonNull).toList());
        }
    }

    @Override
    public void enrichDetailVO(ArticleDetailVO detail) {
        if (detail == null || detail.getId() == null) {
            return;
        }
        List<Long> ids = listCategoryIds(detail.getId());
        if (ids.isEmpty() && detail.getCategoryId() != null) {
            ids = List.of(detail.getCategoryId());
        }
        Map<Long, String> nameMap = categoryNameMap();
        detail.setCategoryIds(ids);
        detail.setCategoryNames(ids.stream().map(nameMap::get).filter(Objects::nonNull).toList());
    }

    private Map<Long, String> categoryNameMap() {
        List<Category> categories = categoryService.listEnabled();
        if (CollectionUtils.isEmpty(categories)) {
            return Map.of();
        }
        return categories.stream()
                .collect(Collectors.toMap(Category::getId, Category::getName, (a, b) -> a, HashMap::new));
    }
}
