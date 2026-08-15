package com.game.community.content.service;

import com.game.community.model.vo.article.ArticleListVO;
import com.game.community.model.vo.game.GameTagVO;

import java.util.List;
import java.util.Map;

public interface ArticleGameService {

    void saveArticleGames(Long articleId, List<Long> appIds);

    List<GameTagVO> listByArticle(Long articleId);

    Map<Long, List<GameTagVO>> listByArticles(List<Long> articleIds);

    void enrichListVOs(List<ArticleListVO> articles);

    /**
     * 批量统计已发布文章关联的游戏讨论数。
     */
    Map<Long, Integer> countPublishedDiscussByAppIds(List<Long> appIds);
}
