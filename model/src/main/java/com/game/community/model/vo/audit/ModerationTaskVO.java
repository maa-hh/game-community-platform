package com.game.community.model.vo.audit;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class ModerationTaskVO implements Serializable {

    /** 对外工单标识，不是数据库主键。 */
    private String taskKey;

    private String taskType;

    private Integer targetType;

    private String targetPublicId;

    private Long subjectAccountId;

    private Long reporterAccountId;

    private String subjectUserName;

    private String reporterName;

    private String reason;

    private String summary;

    private String extraPayload;

    private Integer status;

    private String handleAction;

    private Long handlerAccountId;

    private String handlerName;

    private String handleRemark;

    private LocalDateTime claimTime;

    private LocalDateTime handleTime;

    private LocalDateTime createTime;
}
