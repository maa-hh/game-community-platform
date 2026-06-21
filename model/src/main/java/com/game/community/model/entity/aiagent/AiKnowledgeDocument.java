package com.game.community.model.entity.aiagent;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("t_ai_knowledge_document")
public class AiKnowledgeDocument implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private Integer sourceType;

    private String sourceName;

    private String contentHash;

    private Integer status;

    private Integer indexStatus;

    private Integer segmentCount;

    private Long createdBy;

    private Long updatedBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
