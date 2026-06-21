package com.game.community.model.entity.article;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文章表
 */
@Data
@TableName("t_article")
public class Article implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

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
     * 封面图片URL
     */
    private String coverUrl;

    /**
     * 分类ID
     */
    private Long categoryId;

    /**
     * 文章状态: 0-草稿, 1-已发布, 2-待审核, 3-已下架
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
