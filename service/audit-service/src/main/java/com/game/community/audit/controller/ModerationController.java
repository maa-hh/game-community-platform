package com.game.community.audit.controller;

import com.game.community.audit.service.ModerationService;
import com.game.community.common.annotation.AdminCheck;
import com.game.community.model.base.PageResult;
import com.game.community.model.base.Result;
import com.game.community.model.dto.audit.HandleModerationTaskDTO;
import com.game.community.model.vo.audit.ModerationTaskDetailVO;
import com.game.community.model.vo.audit.ModerationTaskClaimVO;
import com.game.community.model.vo.audit.ModerationTaskVO;
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
@RequestMapping("/audit/moderation")
@RequiredArgsConstructor
public class ModerationController {

    private final ModerationService moderationService;

    @AdminCheck
    @GetMapping("/page")
    public PageResult<ModerationTaskVO> page(@RequestParam(value = "page", defaultValue = "1") Long page,
                                               @RequestParam(value = "size", defaultValue = "20") Long size,
                                               @RequestParam(value = "status", required = false) Integer status,
                                               @RequestParam(value = "taskType", required = false) String taskType) {
        return moderationService.pageTasks(page, size, status, taskType);
    }

    @AdminCheck
    @GetMapping("/{taskId}")
    public Result<ModerationTaskDetailVO> detail(@PathVariable("taskId") Long taskId) {
        return Result.success(moderationService.getDetail(taskId, UserThreadLocal.getUserId()));
    }

    @AdminCheck
    @PostMapping("/{taskId}/claim")
    public Result<ModerationTaskClaimVO> claim(@PathVariable("taskId") Long taskId) {
        return Result.success(moderationService.claim(taskId, UserThreadLocal.getUserId()));
    }

    @AdminCheck
    @PutMapping("/{taskId}")
    public Result<Void> handle(@PathVariable("taskId") Long taskId,
                               @Valid @RequestBody HandleModerationTaskDTO dto) {
        moderationService.handle(taskId, UserThreadLocal.getUserId(), dto);
        return Result.success(null);
    }
}
