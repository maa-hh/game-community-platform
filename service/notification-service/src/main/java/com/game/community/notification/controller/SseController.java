package com.game.community.notification.controller;

import com.game.community.common.annotation.LoginCheck;
import com.game.community.model.vo.notification.NotificationMessageVO;
import com.game.community.model.vo.notification.NotificationSseEventVO;
import com.game.community.model.vo.notification.NotificationSummaryVO;
import com.game.community.common.constant.notification.NotificationConstants;
import com.game.community.notification.service.NotificationService;
import com.game.community.notification.service.SseService;
import com.game.community.utils.ThreadLocal.UserThreadLocal;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/notification/sse")
@RequiredArgsConstructor
public class SseController {

    private final SseService sseService;

    private final NotificationService notificationService;

    @LoginCheck
    @GetMapping(value = "/connect", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter connect(
            @RequestHeader(name = "Last-Event-ID", required = false) String lastEventId,
            HttpServletResponse response) {
        // SSE 必须按事件实时透传，避免 CDN/隧道/反向代理缓存或改写小数据块。
        response.setHeader("Cache-Control", "no-cache, no-transform");
        response.setHeader("X-Accel-Buffering", "no");

        Long userId = UserThreadLocal.getUserId();
        SseEmitter emitter = sseService.connect(userId);
        NotificationSummaryVO summary = notificationService.getSummary(userId);
        try {
            emitter.send(SseEmitter.event()
                    .name(NotificationConstants.SseEventType.NOTIFICATION_SUMMARY)
                    .data(new NotificationSseEventVO(
                            NotificationConstants.SseEventType.NOTIFICATION_SUMMARY,
                            summary,
                            null)));
        } catch (Exception e) {
            emitter.completeWithError(e);
            return emitter;
        }
        Long afterId = parseEventId(lastEventId);
        if (afterId == null) {
            return emitter;
        }
        for (NotificationMessageVO message : notificationService.listMessagesAfterId(userId, afterId, 100)) {
            try {
                emitter.send(SseEmitter.event()
                        .name(NotificationConstants.SseEventType.NOTIFICATION_CREATED)
                        .id(String.valueOf(message.getId()))
                        .data(new NotificationSseEventVO(
                                NotificationConstants.SseEventType.NOTIFICATION_CREATED,
                                summary,
                                message)));
            } catch (Exception e) {
                emitter.completeWithError(e);
                break;
            }
        }
        return emitter;
    }

    private Long parseEventId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
