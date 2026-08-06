package com.game.community.model.vo.article;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonView;

import com.game.community.model.vo.game.GameTagVO;
import com.game.community.model.json.ApiJsonViews;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 文章列表/卡片 VO（对外 API，不含逻辑删除标记）
 */
@Data
public class ArticleListVO implements Serializable {

    @JsonView(ApiJsonViews.Internal.class)
    private Long id;
    /** 对外公开帖子 ID。 */
    @JsonView(ApiJsonViews.Public.class)
    private String publicId;
    /** 作者对外账号 ID */
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
    private List<String> categoryNames;
    @JsonView(ApiJsonViews.Public.class)
    private List<GameTagVO> gameTags;
    @JsonView(ApiJsonViews.Public.class)
    private Integer status;
    @JsonView(ApiJsonViews.Public.class)
    private String auditMessage;
    @JsonView(ApiJsonViews.Public.class)
    private LocalDateTime scheduledPublishTime;
    @JsonView(ApiJsonViews.Public.class)
    private LocalDateTime publishedTime;
    @JsonView(ApiJsonViews.Public.class)
    private LocalDateTime createTime;
    @JsonView(ApiJsonViews.Public.class)
    private LocalDateTime updateTime;
    /** 用户在收藏/点赞列表中的操作时间。普通文章列表不设置。 */
    @JsonView(ApiJsonViews.Public.class)
    private LocalDateTime actionTime;
}
