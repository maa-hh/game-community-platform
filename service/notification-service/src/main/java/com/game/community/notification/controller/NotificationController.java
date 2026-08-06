package com.game.community.notification.controller;

import com.game.community.common.annotation.LoginCheck;
import com.game.community.model.base.PageResult;
import com.game.community.model.base.Result;
import com.game.community.model.vo.notification.NotificationCategorySummaryVO;
import com.game.community.model.vo.notification.NotificationMessageVO;
import com.game.community.model.vo.notification.NotificationSummaryVO;
import com.game.community.notification.service.NotificationService;
import com.game.community.utils.ThreadLocal.UserThreadLocal;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/notification")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @LoginCheck
    @GetMapping("/summary")
    public Result<NotificationSummaryVO> getSummary() {
        return Result.success(notificationService.getSummary(UserThreadLocal.getUserId()));
    }

    @LoginCheck
    @GetMapping("/summary/categories")
    public Result<List<NotificationCategorySummaryVO>> getCategorySummaries() {
        return Result.success(notificationService.getCategorySummaries(UserThreadLocal.getUserId()));
    }

    @LoginCheck
    @GetMapping("/messages")
    public PageResult<NotificationMessageVO> listMessages(
            @RequestParam(name = "page", defaultValue = "1") Long page,
            @RequestParam(name = "size", defaultValue = "20") Long size,
            @RequestParam(name = "eventType", required = false) Integer eventType,
            @RequestParam(name = "category", required = false) String category) {
        Long userId = UserThreadLocal.getUserId();
        if (category != null && !category.isBlank()) {
            return notificationService.listMessagesByCategory(userId, page, size, category);
        }
        return notificationService.listMessages(userId, page, size, eventType);
    }

    @LoginCheck
    @PutMapping("/messages/read-all")
    public Result<NotificationSummaryVO> markAllAsRead() {
        return Result.success(notificationService.markAllAsRead(UserThreadLocal.getUserId()));
    }

    @LoginCheck
    @PutMapping("/messages/read-category")
    public Result<NotificationSummaryVO> markCategoryAsRead(
            @RequestParam(name = "category") String category) {
        return Result.success(notificationService.markCategoryAsRead(UserThreadLocal.getUserId(), category));
    }

    @LoginCheck
    @PutMapping("/feed/read")
    public Result<NotificationSummaryVO> markFeedRead() {
        return Result.success(notificationService.markFeedRead(UserThreadLocal.getUserId()));
    }
}
