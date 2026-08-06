package com.game.community.model.vo.social;

import com.game.community.model.vo.article.ArticleListVO;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class BrowseHistoryVO implements Serializable {

    private Long articleId;

    private String articlePublicId;

    private ArticleListVO article;

    private LocalDateTime browseTime;
}
