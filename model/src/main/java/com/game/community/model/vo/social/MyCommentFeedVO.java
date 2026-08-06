package com.game.community.model.vo.social;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class MyCommentFeedVO implements Serializable {

    private Long id;

    private Long articleId;

    private String articlePublicId;

    private String articleTitle;

    private String content;

    private Long likeCount;

    private Boolean liked;

    private LocalDateTime createTime;

    /** comment | reply */
    private String itemType;

    private Long parentCommentId;

    private String parentCommentContent;

    private String parentUserNickname;

    /** 评论/回复作者昵称（赞过列表等场景） */
    private String authorNickname;

    private Long authorUserId;

    private String authorAvatar;

    private Long authorAccountId;

    private Long parentUserId;

    private String parentUserAvatar;

    private Long parentUserAccountId;

    private Long likerUserId;

    private String likerNickname;

    private String likerAvatar;

    private Long likerAccountId;
}
