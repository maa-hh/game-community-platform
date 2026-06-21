package com.game.community.model.vo.social;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class ReportVO implements Serializable {

    private Long id;

    private Integer targetType;

    private Long targetId;

    private Long reporterId;

    private Long reportedUserId;

    private String reason;

    private Integer status;

    private Long handlerId;

    private String handleRemark;

    private LocalDateTime handleTime;

    private LocalDateTime createTime;
}
