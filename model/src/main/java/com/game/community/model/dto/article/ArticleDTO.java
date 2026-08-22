package com.game.community.model.dto.article;

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
     * 图文/视频介绍的富文本 HTML；content 仍为纯文本，用于摘要、搜索和审核。
     */
    private String contentHtml;

    /**
     * 文章段落内容，按前端编辑顺序保存，例如 {"p1":"第一段","p2":"第二段"}。
     */
    private Map<String, String> contentParagraphs;

    /**
     * 封面图片URL（第一张图片 / pending://）
     */
    private String coverUrl;

    /**
     * 发帖模式: 1-图文, 2-文章, 3-视频, 4-转发；默认文章
     */
    private Integer postType;

    /**
     * 转发引用的原帖 ID（postType=4 时必填）
     */
    /** 转发引用的原帖 publicId（postType=4 时必填）。 */
    private String refArticleId;

    /**
     * 主视频 pending:// 或公网 URL（视频模式）
     */
    private String videoUrl;

    /**
     * 内容中的图片URL列表
     */
    private List<String> imageUrls;

    /**
     * 主分类 ID（兼容旧客户端；新客户端请传 categoryIds，首项为主分类）
     */
    private Long categoryId;

    /**
     * 分类 ID 列表（1~3 个，首项写入 category_id）
     */
    @Size(max = 3, message = "最多选择3个分类")
    private List<Long> categoryIds;

    /**
     * 文章状态: 0-草稿；非草稿一律进入待审核
     */
    private Integer status;

    /**
     * 定时发布时间（为空则立即发布）
     */
    private LocalDateTime scheduledPublishTime;

    /**
     * 关联 Steam 游戏 appId 列表（发帖标签）
     */
    private List<Long> gameAppIds;
}
