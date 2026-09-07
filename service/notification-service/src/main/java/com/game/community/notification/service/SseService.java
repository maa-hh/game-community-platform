package com.game.community.notification.service;

import com.game.community.model.vo.notification.NotificationMessageVO;
import com.game.community.model.vo.notification.NotificationSummaryVO;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

public interface SseService {

    SseEmitter connect(Long userId);

    void sendNotification(Long userId, NotificationMessageVO message, NotificationSummaryVO summary);

    void sendSummary(Long userId, NotificationSummaryVO summary);

    void sendFeedUnread(Long userId, NotificationSummaryVO summary);

    void sendProfileInvalidation(Long userId, String eventId, List<String> domains);
}
