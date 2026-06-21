package com.game.community.model.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReportAuditMessage implements Serializable {

    private Long reportId;

    private Integer targetType;

    private Long targetId;

    private Long reporterId;

    private Long reportedUserId;

    private String reason;

    private LocalDateTime eventTime;
}
