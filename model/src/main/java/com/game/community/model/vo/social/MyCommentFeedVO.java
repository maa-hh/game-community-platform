package com.game.community.model.vo.social;

import com.fasterxml.jackson.annotation.JsonView;
import com.game.community.model.json.ApiJsonViews;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class MyCommentFeedVO implements Serializable {

    @JsonView(ApiJsonViews.Internal.class)
    private Long id;

    @JsonView(ApiJsonViews.Internal.class)
    private Long articleId;

    @JsonView(ApiJsonViews.Public.class)
    private String articlePublicId;

    @JsonView(ApiJsonViews.Public.class)
    private String articleTitle;

    @JsonView(ApiJsonViews.Public.class)
    private String content;

    @JsonView(ApiJsonViews.Public.class)
    private Long likeCount;

    @JsonView(ApiJsonViews.Public.class)
    private Boolean liked;

    @JsonView(ApiJsonViews.Public.class)
    private LocalDateTime createTime;

    /** comment | reply */
    @JsonView(ApiJsonViews.Public.class)
    private String itemType;

    @JsonView(ApiJsonViews.Internal.class)
    private Long parentCommentId;

    @JsonView(ApiJsonViews.Public.class)
    private String parentCommentContent;

    @JsonView(ApiJsonViews.Public.class)
    private String parentUserNickname;

    /** 评论/回复作者昵称（赞过列表等场景） */
    @JsonView(ApiJsonViews.Public.class)
    private String authorNickname;

    @JsonView(ApiJsonViews.Internal.class)
    private Long authorUserId;

    @JsonView(ApiJsonViews.Public.class)
    private String authorAvatar;

    @JsonView(ApiJsonViews.Public.class)
    private Long authorAccountId;

    @JsonView(ApiJsonViews.Internal.class)
    private Long parentUserId;

    @JsonView(ApiJsonViews.Public.class)
    private String parentUserAvatar;

    @JsonView(ApiJsonViews.Public.class)
    private Long parentUserAccountId;

    @JsonView(ApiJsonViews.Internal.class)
    private Long likerUserId;

    @JsonView(ApiJsonViews.Public.class)
    private String likerNickname;

    @JsonView(ApiJsonViews.Public.class)
    private String likerAvatar;

    @JsonView(ApiJsonViews.Public.class)
    private Long likerAccountId;
}
