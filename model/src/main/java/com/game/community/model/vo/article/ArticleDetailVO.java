package com.game.community.model.vo.article;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 文章详情VO（MySQL + MongoDB合并）
 */
@Data
public class ArticleDetailVO implements Serializable {

    private Long id;
    private Long userId;
    private String title;
    private String summary;
    private String coverUrl;
    private Long categoryId;
    private Integer status;
    private String auditMessage;
    private LocalDateTime scheduledPublishTime;
    private LocalDateTime publishedTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    // MongoDB 内容
    private String content;
    private Map<String, String> contentParagraphs;
    private List<String> imageUrls;
}
