package com.game.community.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.game.community.common.constant.content.ContentConstants;
import com.game.community.content.common.converter.ArticleConverter;
import com.game.community.content.mapper.ArticleGameMapper;
import com.game.community.content.mapper.ArticleMapper;
import com.game.community.content.service.ArticleByGameService;
import com.game.community.content.service.ArticleCategoryService;
import com.game.community.content.service.ArticleAuthorEnricher;
import com.game.community.content.service.ArticleGameService;
import com.game.community.model.base.PageResult;
import com.game.community.model.entity.article.Article;
import com.game.community.model.vo.article.ArticleListVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ArticleByGameServiceImpl implements ArticleByGameService {

    private final ArticleGameMapper articleGameMapper;
    private final ArticleMapper articleMapper;
    private final ArticleCategoryService articleCategoryService;
    private final ArticleGameService articleGameService;
    private final ArticleAuthorEnricher articleAuthorEnricher;

    @Override
    public PageResult<ArticleListVO> pageByGame(Long appId, Long page, Long size) {
        long pageNo = page == null || page < 1 ? 1 : page;
        long pageSize = size == null || size < 1 ? 10 : Math.min(size, 50);
        long offset = (pageNo - 1) * pageSize;
        long total = articleGameMapper.countPublishedByAppId(appId);
        List<Long> articleIds = articleGameMapper.selectArticleIdsByAppId(appId, offset, (int) pageSize);
        if (articleIds.isEmpty()) {
            return PageResult.of(List.of(), pageNo, pageSize, total);
        }
        List<Article> articles = articleMapper.selectList(new LambdaQueryWrapper<Article>()
                .in(Article::getId, articleIds)
                .eq(Article::getStatus, ContentConstants.ArticleStatus.PUBLISHED)
                .eq(Article::getDeleted, 0));
        Map<Long, Article> articleMap = articles.stream()
                .collect(Collectors.toMap(Article::getId, Function.identity(), (a, b) -> a));
        List<Article> ordered = new ArrayList<>();
        for (Long articleId : articleIds) {
            Article article = articleMap.get(articleId);
            if (article != null) {
                ordered.add(article);
            }
        }
        List<ArticleListVO> records = ArticleConverter.toListVOs(ordered);
        articleAuthorEnricher.enrich(ordered, records);
        articleCategoryService.enrichListVOs(records);
        articleGameService.enrichListVOs(records);
        return PageResult.of(records, pageNo, pageSize, total);
    }
}
