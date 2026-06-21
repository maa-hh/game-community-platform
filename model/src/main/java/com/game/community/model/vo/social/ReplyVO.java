package com.game.community.model.vo.social;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class ReplyVO implements Serializable {

    private Long id;

    private Long commentId;

    private Long articleId;

    private Long userId;

    private String username;

    private String avatar;

    private Long replyToUserId;

    private String replyToUsername;

    private String content;

    private Long likeCount;

    private Boolean liked;

    private LocalDateTime createTime;
}
