package com.game.community.model.vo.article;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 文章正文 VO（Mongo 内容对外暴露）
 */
@Data
public class ArticleContentVO implements Serializable {

    private Long articleId;
    private String content;
    private String contentHtml;
    private Map<String, String> contentParagraphs;
    private List<String> imageUrls;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
