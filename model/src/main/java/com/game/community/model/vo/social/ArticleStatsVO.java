package com.game.community.model.vo.social;

import com.fasterxml.jackson.annotation.JsonView;
import com.game.community.model.json.ApiJsonViews;
import lombok.Data;

import java.io.Serializable;

@Data
public class ArticleStatsVO implements Serializable {

    @JsonView(ApiJsonViews.Internal.class)
    private Long articleId;

    @JsonView(ApiJsonViews.Public.class)
    private String publicId;

    @JsonView(ApiJsonViews.Public.class)
    private Long likeCount;

    @JsonView(ApiJsonViews.Public.class)
    private Long commentCount;

    @JsonView(ApiJsonViews.Public.class)
    private Long commentLikeCount;

    @JsonView(ApiJsonViews.Public.class)
    private Long replyCount;

    @JsonView(ApiJsonViews.Public.class)
    private Long replyLikeCount;

    @JsonView(ApiJsonViews.Public.class)
    private Long viewCount;

    @JsonView(ApiJsonViews.Public.class)
    private Long favoriteCount;

    @JsonView(ApiJsonViews.Public.class)
    private Long shareCount;

    @JsonView(ApiJsonViews.Public.class)
    private Boolean liked;

    @JsonView(ApiJsonViews.Public.class)
    private Boolean favorited;
}
