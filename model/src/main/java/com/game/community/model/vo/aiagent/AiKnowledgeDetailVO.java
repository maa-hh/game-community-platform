package com.game.community.model.vo.aiagent;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class AiKnowledgeDetailVO implements Serializable {

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

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
