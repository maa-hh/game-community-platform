package com.game.community.model.entity.search;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("t_suggest_term")
public class SuggestTerm implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String term;

    private String sourceType;

    private Long sourceArticleId;

    private Integer weight;

    private Integer pinned;

    private String status;

    private Long triggerCount;

    private LocalDateTime createdAt;

    private LocalDateTime lastTriggeredAt;

    private LocalDateTime updatedAt;
}
