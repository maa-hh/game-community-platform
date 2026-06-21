package com.game.community.model.vo.audit;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class AuditReportVO implements Serializable {

    private Long id;

    private Long reportId;

    private Integer targetType;

    private Long targetId;

    private Long reporterId;

    private Long reportedUserId;

    private String reporterName;

    private String reportedUserName;

    private String reason;

    private Integer status;

    private Long handlerId;

    private String handleRemark;

    private LocalDateTime handleTime;

    private LocalDateTime createTime;
}
