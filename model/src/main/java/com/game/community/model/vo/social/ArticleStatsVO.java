package com.game.community.model.vo.social;

import lombok.Data;

import java.io.Serializable;

@Data
public class ArticleStatsVO implements Serializable {

    private Long articleId;

    private Long likeCount;

    private Long commentCount;

    private Long commentLikeCount;

    private Long replyCount;

    private Long replyLikeCount;

    private Long viewCount;

    private Boolean liked;
}
