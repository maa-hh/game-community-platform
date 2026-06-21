package com.game.community.model.vo.article;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class HotArticleVO implements Serializable {

    private Long id;

    private Long userId;

    private String title;

    private String summary;

    private String coverUrl;

    private Long categoryId;

    private String categoryName;

    private String authorName;

    private String authorAvatar;

    private Long likeCount;

    private Long commentCount;

    private Long commentLikeCount;

    private Long replyCount;

    private Long replyLikeCount;

    private Long viewCount;

    private Boolean liked;

    private Double hotScore;

    private LocalDateTime publishedTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
