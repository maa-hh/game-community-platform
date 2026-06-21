package com.game.community.notification.service.impl;

import com.game.community.common.constant.notification.NotificationConstants;
import com.game.community.model.vo.notification.NotificationMessageVO;
import com.game.community.model.vo.notification.NotificationSseEventVO;
import com.game.community.model.vo.notification.NotificationSummaryVO;
import com.game.community.notification.service.SseService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class SseServiceImpl implements SseService {

    private final Map<Long, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();

    @Override
    public SseEmitter connect(Long userId) {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.computeIfAbsent(userId, key -> ConcurrentHashMap.newKeySet()).add(emitter);
        emitter.onCompletion(() -> removeEmitter(userId, emitter));
        emitter.onTimeout(() -> removeEmitter(userId, emitter));
        emitter.onError(error -> removeEmitter(userId, emitter));
        return emitter;
    }

    @Override
    public void sendNotification(Long userId, NotificationMessageVO message, NotificationSummaryVO summary) {
        NotificationSseEventVO payload = new NotificationSseEventVO(
                NotificationConstants.SseEventType.NOTIFICATION_CREATED,
                summary,
                message
        );
        send(userId, NotificationConstants.SseEventType.NOTIFICATION_CREATED, payload);
    }

    @Override
    public void sendSummary(Long userId, NotificationSummaryVO summary) {
        NotificationSseEventVO payload = new NotificationSseEventVO(
                NotificationConstants.SseEventType.NOTIFICATION_SUMMARY,
                summary,
                null
        );
        send(userId, NotificationConstants.SseEventType.NOTIFICATION_SUMMARY, payload);
    }

    @Override
    public void sendFeedUnread(Long userId, NotificationSummaryVO summary) {
        NotificationSseEventVO payload = new NotificationSseEventVO(
                NotificationConstants.SseEventType.FEED_UNREAD,
                summary,
                null
        );
        send(userId, NotificationConstants.SseEventType.FEED_UNREAD, payload);
    }

    @Scheduled(fixedDelay = 25000L)
    public void heartbeat() {
        NotificationSseEventVO payload = new NotificationSseEventVO(
                NotificationConstants.SseEventType.HEARTBEAT,
                null,
                null
        );
        for (Long userId : emitters.keySet()) {
            send(userId, NotificationConstants.SseEventType.HEARTBEAT, payload);
        }
    }

    private void send(Long userId, String eventName, NotificationSseEventVO payload) {
        Set<SseEmitter> connections = emitters.get(userId);
        if (connections == null || connections.isEmpty()) {
            return;
        }
        Iterator<SseEmitter> iterator = connections.iterator();
        while (iterator.hasNext()) {
            SseEmitter emitter = iterator.next();
            try {
                emitter.send(SseEmitter.event().name(eventName).data(payload));
            } catch (IOException e) {
                log.warn("SSE发送失败, userId={}, event={}, error={}", userId, eventName, e.getMessage());
                iterator.remove();
                safeComplete(emitter);
            }
        }
        if (connections.isEmpty()) {
            emitters.remove(userId);
        }
    }

    private void removeEmitter(Long userId, SseEmitter emitter) {
        Set<SseEmitter> connections = emitters.get(userId);
        if (connections != null) {
            connections.remove(emitter);
            if (connections.isEmpty()) {
                emitters.remove(userId);
            }
        }
        safeComplete(emitter);
    }

    private void safeComplete(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (Exception ignored) {
            // ignore
        }
    }
}
