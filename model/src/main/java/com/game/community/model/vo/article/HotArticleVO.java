package com.game.community.model.vo.article;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class HotArticleVO implements Serializable {

    private Long id;

    private String publicId;

    private Integer rank;

    private Long authorAccountId;

    private String title;

    private String summary;

    private String coverUrl;

    private Integer postType;

    private String refArticleId;

    private String videoUrl;

    private Long categoryId;

    private List<Long> categoryIds;

    private String categoryName;

    private List<String> categoryNames;

    private String boardType;

    private String periodKey;

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
