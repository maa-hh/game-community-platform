package com.game.community.model.vo.social;

import lombok.Data;

import java.io.Serializable;

@Data
public class GameReviewSocialStatsVO implements Serializable {

    private String reviewId;

    private Long likeCount;

    private Long replyCount;

    private Boolean liked;

    /** 短评正文由 Social-service 从 MongoDB 返回，Steam-service 仅保留评分。 */
    private String content;
}
