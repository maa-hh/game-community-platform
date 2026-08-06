package com.game.community.social.controller;

import com.game.community.common.annotation.AdminCheck;
import com.game.community.common.annotation.LoginCheck;
import com.game.community.model.base.PageResult;
import com.game.community.model.base.Result;
import com.game.community.model.dto.social.CreateReportDTO;
import com.game.community.model.dto.social.HandleReportDTO;
import com.game.community.model.vo.social.ReportVO;
import com.game.community.social.service.ReportService;
import com.game.community.social.aspect.SocialRateLimit;
import com.game.community.utils.ThreadLocal.UserThreadLocal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/report")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @LoginCheck
    @SocialRateLimit(action = "report:create", limit = 5, windowSeconds = 300)
    @PostMapping
    public Result<Long> createReport(@Valid @RequestBody CreateReportDTO dto) {
        return Result.success(reportService.createReport(UserThreadLocal.getUserId(), dto));
    }

    @AdminCheck
    @PutMapping("/{reportId}")
    public Result<Void> handleReport(@PathVariable("reportId") Long reportId,
                                     @Valid @RequestBody HandleReportDTO dto) {
        reportService.handleReport(UserThreadLocal.getUserId(), reportId, dto);
        return Result.success(null);
    }

    @AdminCheck
    @GetMapping("/page")
    public PageResult<ReportVO> pageReports(@RequestParam(value = "page", defaultValue = "1") Long page,
                                            @RequestParam(value = "size", defaultValue = "20") Long size,
                                            @RequestParam(value = "status", required = false) Integer status,
                                            @RequestParam(value = "targetType", required = false) Integer targetType) {
        return reportService.pageReports(page, size, status, targetType);
    }
}
