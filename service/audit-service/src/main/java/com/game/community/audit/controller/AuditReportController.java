package com.game.community.audit.controller;

import com.game.community.audit.service.AuditReportService;
import com.game.community.common.annotation.AdminCheck;
import com.game.community.model.base.PageResult;
import com.game.community.model.base.Result;
import com.game.community.model.dto.audit.HandleAuditReportDTO;
import com.game.community.model.vo.audit.AuditReportDetailVO;
import com.game.community.model.vo.audit.AuditReportVO;
import com.game.community.utils.ThreadLocal.UserThreadLocal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/audit/report")
@RequiredArgsConstructor
public class AuditReportController {

    private final AuditReportService auditReportService;

    @AdminCheck
    @GetMapping("/page")
    public PageResult<AuditReportVO> pageReports(@RequestParam(value = "page", defaultValue = "1") Long page,
                                                 @RequestParam(value = "size", defaultValue = "20") Long size,
                                                 @RequestParam(value = "status", required = false) Integer status,
                                                 @RequestParam(value = "targetType", required = false) Integer targetType) {
        return auditReportService.pageReports(page, size, status, targetType);
    }

    @AdminCheck
    @GetMapping("/{taskId}")
    public Result<AuditReportDetailVO> getDetail(@PathVariable("taskId") Long taskId) {
        return Result.success(auditReportService.getDetail(taskId));
    }

    @AdminCheck
    @PutMapping("/{taskId}")
    public Result<Void> handle(@PathVariable("taskId") Long taskId,
                               @Valid @RequestBody HandleAuditReportDTO dto) {
        auditReportService.handle(taskId, UserThreadLocal.getUserId(), dto);
        return Result.success(null);
    }
}
