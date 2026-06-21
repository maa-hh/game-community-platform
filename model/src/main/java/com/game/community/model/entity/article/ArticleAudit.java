package com.game.community.model.entity.article;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文章审核记录表
 */
@Data
@TableName("t_article_audit")
public class ArticleAudit implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 文章ID
     */
    private Long articleId;

    private Integer auditStage;

    /**
     * 审核结果: 0-待审核, 1-通过, 2-疑似, 3-违规
     */
    private Integer status;

    /**
     * 审核建议: pass, review, block
     */
    private String suggestion;

    /**
     * 违规原因
     */
    private String reason;

    /**
     * 审核时间
     */
    private LocalDateTime auditTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
