package com.game.community.model.vo.social;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class CommentVO implements Serializable {

    private Long id;

    private Long articleId;

    private Long accountId;

    private String username;

    private String avatar;

    private String content;

    private Long likeCount;

    private Long replyCount;

    private Boolean liked;

    private LocalDateTime createTime;
}
