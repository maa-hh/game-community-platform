package com.game.community.model.vo.social;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class GameReviewReplyVO implements Serializable {

    private String replyId;

    private String reviewId;

    private Long accountId;

    private String username;

    private String avatar;

    private Long replyToAccountId;

    private String replyToUsername;

    private String content;

    private Long likeCount;

    private Boolean liked;

    private LocalDateTime createTime;
}
