package com.game.community.model.dto.article;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 文章创建/更新DTO
 */
@Data
public class ArticleDTO implements Serializable {

    /**
     * 文章ID（更新时使用）
     */
    private Long id;

    /**
     * 文章标题
     */
    @NotBlank(message = "标题不能为空")
    @Size(max = 80, message = "标题长度不能超过80个字符")
    private String title;

    /**
     * 文章摘要
     */
    @Size(max = 200, message = "摘要长度不能超过200个字符")
    private String summary;

    /**
     * 文章内容（纯文本）
     */
    private String content;

    /**
     * 文章段落内容，按前端编辑顺序保存，例如 {"p1":"第一段","p2":"第二段"}。
     */
    private Map<String, String> contentParagraphs;

    /**
     * 封面图片URL（第一张图片）
     */
    private String coverUrl;

    /**
     * 内容中的图片URL列表
     */
    private List<String> imageUrls;

    /**
     * 分类ID
     */
    @NotNull(message = "分类不能为空")
    private Long categoryId;

    /**
     * 文章状态: 0-草稿, 1-已发布, 2-待审核
     */
    private Integer status;

    /**
     * 定时发布时间（为空则立即发布）
     */
    private LocalDateTime scheduledPublishTime;
}
