package com.game.community.model.entity.article;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 文章表
 */
@Data
@TableName(value = "t_article", autoResultMap = true)
public class Article implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    @Version
    private Long version;

    /** 对外公开帖子 ID；不参与内部表关联。 */
    private String publicId;

    /**
     * 作者ID
     */
    private Long userId;

    /**
     * 文章标题
     */
    private String title;

    /**
     * 文章摘要
     */
    private String summary;

    /**
     * 封面图片URL（发布后为公网；待审可为 pending://objectKey）
     */
    private String coverUrl;

    /**
     * 发帖模式: 1-图文, 2-文章, 3-视频, 4-转发
     */
    private Integer postType;

    /**
     * 转发引用的原帖 ID（postType=4 时有效）
     */
    /** 转发引用的原帖 publicId（仅 postType=4 时有效）。 */
    private String refArticleId;

    /**
     * 主视频地址（视频模式；待审可为 pending://objectKey）
     */
    private String videoUrl;

    /**
     * 主分类 ID（列表首项，兼容旧索引）
     */
    private Long categoryId;

    /**
     * 分类 ID 列表（JSON 数组，1~3 个）
     */
    @TableField(value = "category_ids", typeHandler = JacksonTypeHandler.class)
    private List<Long> categoryIds;

    /**
     * 文章状态: 0-草稿, 1-已发布, 2-待审核, 3-已下架, 4-审核驳回
     */
    private Integer status;

    /**
     * 最近一次审核消息或下架原因
     */
    private String auditMessage;

    /**
     * 计划发布时间
     */
    private LocalDateTime scheduledPublishTime;

    /**
     * 实际发布时间
     */
    private LocalDateTime publishedTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
