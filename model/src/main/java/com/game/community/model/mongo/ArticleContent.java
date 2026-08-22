package com.game.community.model.mongo;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 文章内容表 - MongoDB存储
 */
@Document(collection = "t_article_content")
public class ArticleContent {

    @Id
    private String id;

    @Indexed(unique = true)
    private Long articleId;

    /**
     * 文章正文（纯文本）
     */
    private String content;

    /**
     * 图文正文或视频介绍的富文本 HTML，服务端保存前已按允许标签清洗。
     */
    private String contentHtml;

    /**
     * 结构化段落正文，保持前端编辑顺序。
     */
    private Map<String, String> contentParagraphs;

    /**
     * 内容中的图片URL列表
     */
    private List<String> imageUrls;

    @Indexed
    private Long userId;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public Long getArticleId() { return articleId; }
    public void setArticleId(Long articleId) { this.articleId = articleId; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getContentHtml() { return contentHtml; }
    public void setContentHtml(String contentHtml) { this.contentHtml = contentHtml; }
    public Map<String, String> getContentParagraphs() { return contentParagraphs; }
    public void setContentParagraphs(Map<String, String> contentParagraphs) { this.contentParagraphs = contentParagraphs; }
    public List<String> getImageUrls() { return imageUrls; }
    public void setImageUrls(List<String> imageUrls) { this.imageUrls = imageUrls; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
