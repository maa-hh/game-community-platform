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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class SseServiceImpl implements SseService {

    private final Map<Long, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final Map<SseEmitter, EmitterState> emitterStates = new ConcurrentHashMap<>();

    private final StringRedisTemplate redisTemplate;

    private final ObjectMapper objectMapper;

    public SseServiceImpl(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public SseEmitter connect(Long userId) {
        SseEmitter emitter = new SseEmitter(0L);
        emitterStates.put(emitter, new EmitterState());
        emitters.computeIfAbsent(userId, key -> ConcurrentHashMap.newKeySet()).add(emitter);
        emitter.onCompletion(() -> removeEmitter(userId, emitter));
        emitter.onTimeout(() -> removeEmitter(userId, emitter));
        emitter.onError(error -> removeEmitter(userId, emitter));
        return emitter;
    }

    @Override
    public void initialize(SseEmitter emitter, NotificationSummaryVO summary,
                           List<NotificationMessageVO> replayMessages) {
        EmitterState state = emitterStates.get(emitter);
        if (state == null) {
            return;
        }
        synchronized (emitter) {
            try {
                sendEvent(emitter, NotificationConstants.SseEventType.NOTIFICATION_SUMMARY,
                        new NotificationSseEventVO(
                                NotificationConstants.SseEventType.NOTIFICATION_SUMMARY,
                                summary,
                                null));
                long replayedThroughId = 0L;
                if (replayMessages != null) {
                    for (NotificationMessageVO message : replayMessages) {
                        if (message == null) {
                            continue;
                        }
                        replayedThroughId = Math.max(replayedThroughId,
                                message.getId() == null ? 0L : message.getId());
                        sendEvent(emitter, NotificationConstants.SseEventType.NOTIFICATION_CREATED,
                                new NotificationSseEventVO(
                                        NotificationConstants.SseEventType.NOTIFICATION_CREATED,
                                        summary,
                                        message));
                    }
                }
                state.ready = true;
                List<NotificationSseBroadcast> pending = new ArrayList<>(state.pending);
                state.pending.clear();
                pending.sort(Comparator.comparingLong(this::notificationId));
                for (NotificationSseBroadcast broadcast : pending) {
                    if (isNotificationAlreadyReplayed(broadcast, replayedThroughId)) {
                        continue;
                    }
                    sendEvent(emitter, broadcast.getEventName(), broadcast.getPayload());
                }
            } catch (IOException | RuntimeException e) {
                removeEmitter(emitterUserId(emitter), emitter);
                safeComplete(emitter);
                log.debug("SSE初始化失败，关闭连接", e);
            }
        }
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

    @Override
    public void sendProfileInvalidation(Long userId, String eventId, List<String> domains) {
        NotificationSseEventVO payload = new NotificationSseEventVO(
                NotificationConstants.SseEventType.PROFILE_INVALIDATED,
                null,
                null,
                eventId,
                domains
        );
        publish(new NotificationSseBroadcast(userId,
                NotificationConstants.SseEventType.PROFILE_INVALIDATED, payload));
    }

    @Scheduled(fixedDelay = 10000L)
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
        Set<SseEmitter> connections = emitters.get(broadcast.getUserId());
        if (connections == null || connections.isEmpty()) {
            return;
        }
        Iterator<SseEmitter> iterator = connections.iterator();
        while (iterator.hasNext()) {
            SseEmitter emitter = iterator.next();
            EmitterState state = emitterStates.get(emitter);
            if (state == null) {
                iterator.remove();
                continue;
            }
            try {
                synchronized (emitter) {
                    if (!state.ready) {
                        state.pending.add(broadcast);
                    } else {
                        sendEvent(emitter, broadcast.getEventName(), broadcast.getPayload());
                    }
                }
            } catch (IOException | RuntimeException e) {
                log.warn("SSE发送失败, userId={}, event={}, error={}",
                        broadcast.getUserId(), broadcast.getEventName(), e.getMessage());
                iterator.remove();
                removeEmitter(broadcast.getUserId(), emitter);
                safeComplete(emitter);
            }
        }
        if (connections.isEmpty()) {
            emitters.remove(broadcast.getUserId(), connections);
        }
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

    private void removeEmitter(Long userId, SseEmitter emitter) {
        emitterStates.remove(emitter);
        if (userId == null) {
            return;
        }
        Set<SseEmitter> connections = emitters.get(userId);
        if (connections != null) {
            connections.remove(emitter);
            if (connections.isEmpty()) {
                emitters.remove(userId);
            }
        }
    }

    private void sendEvent(SseEmitter emitter, String eventName,
                           NotificationSseEventVO payload) throws IOException {
        SseEmitter.SseEventBuilder builder = SseEmitter.event()
                .name(eventName)
                .data(payload);
        if (payload != null && payload.getEventId() != null) {
            builder.id(payload.getEventId());
        } else if (payload != null && payload.getMessage() != null
                && payload.getMessage().getId() != null) {
            builder.id(String.valueOf(payload.getMessage().getId()));
        }
        emitter.send(builder);
    }

    private long notificationId(NotificationSseBroadcast broadcast) {
        if (broadcast == null || broadcast.getPayload() == null
                || broadcast.getPayload().getMessage() == null
                || broadcast.getPayload().getMessage().getId() == null) {
            return Long.MAX_VALUE;
        }
        return broadcast.getPayload().getMessage().getId();
    }

    private boolean isNotificationAlreadyReplayed(NotificationSseBroadcast broadcast,
                                                   long replayedThroughId) {
        return broadcast != null
                && NotificationConstants.SseEventType.NOTIFICATION_CREATED.equals(broadcast.getEventName())
                && notificationId(broadcast) <= replayedThroughId;
    }

    private Long emitterUserId(SseEmitter emitter) {
        for (Map.Entry<Long, Set<SseEmitter>> entry : emitters.entrySet()) {
            if (entry.getValue().contains(emitter)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private static final class EmitterState {
        private boolean ready;
        private final List<NotificationSseBroadcast> pending = new ArrayList<>();
    }

    private void safeComplete(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (Exception ignored) {
            // ignore
        }
    }
}
