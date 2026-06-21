package com.game.community.recommend.service;

import com.game.community.model.base.PageResult;
import com.game.community.model.vo.article.HotArticleVO;

public interface HotArticleService {

    void calculateHotArticles();

    void updateHotScore(Long articleId);

    PageResult<HotArticleVO> listHotArticles(Long userId, Long page, Long size);

    PageResult<HotArticleVO> listCategoryHotArticles(Long userId, Long categoryId, Long page, Long size);
}
