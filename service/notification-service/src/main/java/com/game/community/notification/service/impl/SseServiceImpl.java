package com.game.community.notification.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.community.common.constant.notification.NotificationConstants;
import com.game.community.model.vo.notification.NotificationMessageVO;
import com.game.community.model.vo.notification.NotificationSseEventVO;
import com.game.community.model.vo.notification.NotificationSummaryVO;
import com.game.community.notification.config.NotificationRedisConfig;
import com.game.community.notification.service.SseService;
import com.game.community.notification.sse.NotificationSseBroadcast;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.StringRedisTemplate;
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

    private final StringRedisTemplate redisTemplate;

    private final ObjectMapper objectMapper;

    public SseServiceImpl(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

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
        publish(new NotificationSseBroadcast(userId,
                NotificationConstants.SseEventType.NOTIFICATION_CREATED, payload));
    }

    @Override
    public void sendSummary(Long userId, NotificationSummaryVO summary) {
        NotificationSseEventVO payload = new NotificationSseEventVO(
                NotificationConstants.SseEventType.NOTIFICATION_SUMMARY,
                summary,
                null
        );
        publish(new NotificationSseBroadcast(userId,
                NotificationConstants.SseEventType.NOTIFICATION_SUMMARY, payload));
    }

    @Override
    public void sendFeedUnread(Long userId, NotificationSummaryVO summary) {
        NotificationSseEventVO payload = new NotificationSseEventVO(
                NotificationConstants.SseEventType.FEED_UNREAD,
                summary,
                null
        );
        publish(new NotificationSseBroadcast(userId,
                NotificationConstants.SseEventType.FEED_UNREAD, payload));
    }

    @Scheduled(fixedDelay = 25000L)
    public void heartbeat() {
        NotificationSseEventVO payload = new NotificationSseEventVO(
                NotificationConstants.SseEventType.HEARTBEAT,
                null,
                null
        );
        for (Long userId : emitters.keySet()) {
            sendLocal(new NotificationSseBroadcast(userId,
                    NotificationConstants.SseEventType.HEARTBEAT, payload));
        }
    }

    public void sendLocal(NotificationSseBroadcast broadcast) {
        if (broadcast == null || broadcast.getUserId() == null) {
            return;
        }
        sendLocal(broadcast.getUserId(), broadcast.getEventName(), broadcast.getPayload());
    }

    private void publish(NotificationSseBroadcast broadcast) {
        try {
            redisTemplate.convertAndSend(
                    NotificationRedisConfig.SSE_CHANNEL,
                    objectMapper.writeValueAsString(broadcast));
        } catch (JsonProcessingException | RuntimeException e) {
            log.warn("通知 SSE Redis 广播失败，回退本机推送: userId={}, event={}",
                    broadcast.getUserId(), broadcast.getEventName(), e);
            sendLocal(broadcast);
        }
    }

    private void sendLocal(Long userId, String eventName, NotificationSseEventVO payload) {
        Set<SseEmitter> connections = emitters.get(userId);
        if (connections == null || connections.isEmpty()) {
            return;
        }
        Iterator<SseEmitter> iterator = connections.iterator();
        while (iterator.hasNext()) {
            SseEmitter emitter = iterator.next();
            try {
                synchronized (emitter) {
                    SseEmitter.SseEventBuilder builder = SseEmitter.event()
                            .name(eventName)
                            .data(payload);
                    if (payload != null && payload.getMessage() != null
                            && payload.getMessage().getId() != null) {
                        builder.id(String.valueOf(payload.getMessage().getId()));
                    }
                    emitter.send(builder);
                }
            } catch (IOException | RuntimeException e) {
                log.warn("SSE发送失败, userId={}, event={}, error={}", userId, eventName, e.getMessage());
                iterator.remove();
                safeComplete(emitter);
            }
        }
        if (connections.isEmpty()) {
            emitters.remove(userId, connections);
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
