package com.game.community.notification.controller;

import com.game.community.common.annotation.LoginCheck;
import com.game.community.notification.service.SseService;
import com.game.community.utils.ThreadLocal.UserThreadLocal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/notification/sse")
@RequiredArgsConstructor
public class SseController {

    private final SseService sseService;

    @LoginCheck
    @GetMapping(value = "/connect", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter connect() {
        return sseService.connect(UserThreadLocal.getUserId());
    }
}
