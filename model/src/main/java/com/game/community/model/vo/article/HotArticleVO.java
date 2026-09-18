package com.game.community.model.vo.article;

import com.fasterxml.jackson.annotation.JsonView;
import com.game.community.model.json.ApiJsonViews;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class HotArticleVO implements Serializable {

    /** 内部数据库主键只供服务间使用，公开热榜统一通过 publicId 定位帖子。 */
    @JsonView(ApiJsonViews.Internal.class)
    private Long id;

    @JsonView(ApiJsonViews.Public.class)
    private String publicId;

    @JsonView(ApiJsonViews.Public.class)
    private Integer rank;

    @JsonView(ApiJsonViews.Public.class)
    private Long authorAccountId;

    @JsonView(ApiJsonViews.Public.class)
    private String title;

    @JsonView(ApiJsonViews.Public.class)
    private String summary;

    @JsonView(ApiJsonViews.Public.class)
    private String coverUrl;

    @JsonView(ApiJsonViews.Public.class)
    private Integer postType;

    @JsonView(ApiJsonViews.Public.class)
    private String refArticleId;

    @JsonView(ApiJsonViews.Public.class)
    private String videoUrl;

    @JsonView(ApiJsonViews.Public.class)
    private Long categoryId;

    @JsonView(ApiJsonViews.Public.class)
    private List<Long> categoryIds;

    @JsonView(ApiJsonViews.Public.class)
    private String categoryName;

    @JsonView(ApiJsonViews.Public.class)
    private List<String> categoryNames;

    @JsonView(ApiJsonViews.Public.class)
    private String boardType;

    @JsonView(ApiJsonViews.Public.class)
    private String periodKey;

    @JsonView(ApiJsonViews.Public.class)
    private String authorName;

    @JsonView(ApiJsonViews.Public.class)
    private String authorAvatar;

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
    private Boolean liked;

    @JsonView(ApiJsonViews.Public.class)
    private Double hotScore;

    @JsonView(ApiJsonViews.Public.class)
    private LocalDateTime publishedTime;

    @JsonView(ApiJsonViews.Public.class)
    private LocalDateTime createTime;

    @JsonView(ApiJsonViews.Public.class)
    private LocalDateTime updateTime;
}
