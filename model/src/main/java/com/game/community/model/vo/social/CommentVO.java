package com.game.community.model.vo.social;

import com.fasterxml.jackson.annotation.JsonView;
import com.game.community.model.json.ApiJsonViews;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class CommentVO implements Serializable {

    /** 内部评论主键，仅供服务间调用；公开接口使用评论所在资源上下文。 */
    @JsonView(ApiJsonViews.Internal.class)
    private Long id;

    @JsonView(ApiJsonViews.Internal.class)
    private Long articleId;

    @JsonView(ApiJsonViews.Public.class)
    private Long accountId;

    @JsonView(ApiJsonViews.Public.class)
    private String username;

    @JsonView(ApiJsonViews.Public.class)
    private String avatar;

    @JsonView(ApiJsonViews.Public.class)
    private String content;

    @JsonView(ApiJsonViews.Public.class)
    private Long likeCount;

    @JsonView(ApiJsonViews.Public.class)
    private Long replyCount;

    @JsonView(ApiJsonViews.Public.class)
    private Boolean liked;

    @JsonView(ApiJsonViews.Public.class)
    private LocalDateTime createTime;
}
