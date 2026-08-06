package com.game.community.content.service;

import com.game.community.model.base.PageResult;
import com.game.community.model.vo.article.ArticleListVO;

public interface ArticleByGameService {

    PageResult<ArticleListVO> pageByGame(Long appId, Long page, Long size);
}
