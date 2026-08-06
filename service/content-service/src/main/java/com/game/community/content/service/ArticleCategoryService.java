package com.game.community.content.service;

import com.game.community.model.vo.article.ArticleDetailVO;
import com.game.community.model.vo.article.ArticleListVO;

import java.util.List;
import java.util.Map;

public interface ArticleCategoryService {

    List<Long> listCategoryIds(Long articleId);

    void replaceCategoryRelations(Long articleId, List<Long> categoryIds);

    Map<Long, List<Long>> mapCategoryIdsByArticleIds(List<Long> articleIds);

    void enrichListVOs(List<ArticleListVO> articles);

    void enrichDetailVO(ArticleDetailVO detail);
}
