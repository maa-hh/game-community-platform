package com.game.community.audit.service;

import com.game.community.model.base.PageResult;
import com.game.community.model.dto.audit.HandleAuditReportDTO;
import com.game.community.model.message.ReportAuditMessage;
import com.game.community.model.vo.audit.AuditReportDetailVO;
import com.game.community.model.vo.audit.AuditReportVO;

public interface AuditReportService {

    void receiveReport(ReportAuditMessage message);

    PageResult<AuditReportVO> pageReports(Long page, Long size, Integer status, Integer targetType);

    AuditReportDetailVO getDetail(Long taskId);

    void handle(Long taskId, Long handlerId, HandleAuditReportDTO dto);
}
