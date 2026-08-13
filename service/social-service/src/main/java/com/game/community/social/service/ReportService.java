package com.game.community.social.service;

import com.game.community.model.base.PageResult;
import com.game.community.model.dto.social.CreateReportDTO;
import com.game.community.model.dto.social.HandleReportDTO;
import com.game.community.model.dto.social.ReportPageQueryDTO;
import com.game.community.model.vo.social.ReportVO;

public interface ReportService {

    Long createReport(Long userId, CreateReportDTO dto);

    void handleReport(Long handlerId, Long reportId, HandleReportDTO dto);

    void markReportHandled(Long handlerId, Long reportId, Integer status, String handleRemark);

    PageResult<ReportVO> pageReports(ReportPageQueryDTO query);
}
