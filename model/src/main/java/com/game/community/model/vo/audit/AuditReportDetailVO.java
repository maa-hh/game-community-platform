package com.game.community.model.vo.audit;

import lombok.Data;

import java.io.Serializable;

@Data
public class AuditReportDetailVO extends AuditReportVO implements Serializable {

    private String targetTitle;

    private String targetContent;

    private String targetAuthorName;

    private Object target;
}
