package com.game.community.model.vo.social;

import com.fasterxml.jackson.annotation.JsonView;
import com.game.community.model.json.ApiJsonViews;
import com.game.community.model.vo.article.ArticleListVO;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class BrowseHistoryVO implements Serializable {

    @JsonView(ApiJsonViews.Internal.class)
    private Long articleId;

    @JsonView(ApiJsonViews.Public.class)
    private String articlePublicId;

    @JsonView(ApiJsonViews.Public.class)
    private ArticleListVO article;

    @JsonView(ApiJsonViews.Public.class)
    private LocalDateTime browseTime;
}
