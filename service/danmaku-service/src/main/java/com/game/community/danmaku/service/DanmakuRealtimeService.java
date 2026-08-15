package com.game.community.danmaku.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.community.danmaku.common.constant.DanmakuCacheConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 节点间只广播在线实时消息；可靠历史由 Kafka + MySQL/Redis 查询链路负责。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DanmakuRealtimeService {

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final RedisMessageListenerContainer container;

    private final Map<String, Set<WebSocketSession>> rooms = new ConcurrentHashMap<>();
    private final Map<String, MessageListener> listeners = new ConcurrentHashMap<>();

    public synchronized void register(String videoPublicId, WebSocketSession session) {
        rooms.computeIfAbsent(videoPublicId, ignored -> ConcurrentHashMap.newKeySet()).add(session);
        listeners.computeIfAbsent(videoPublicId, this::subscribe);
    }

    public synchronized void unregister(String videoPublicId, WebSocketSession session) {
        Set<WebSocketSession> sessions = rooms.get(videoPublicId);
        if (sessions == null) {
            return;
        }
        if (session != null) {
            sessions.remove(session);
        }
        if (!sessions.isEmpty()) {
            return;
        }
        rooms.remove(videoPublicId);
        MessageListener listener = listeners.remove(videoPublicId);
        if (listener != null) {
            container.removeMessageListener(listener, new ChannelTopic(channel(videoPublicId)));
        }
    }

    public void publish(String videoPublicId, DanmakuRealtimeMessage payload) {
        try {
            redis.convertAndSend(channel(videoPublicId), objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            log.error("弹幕广播消息序列化失败，video={}", videoPublicId, e);
        } catch (RuntimeException e) {
            // 实时广播是加速通道，Kafka/历史查询仍是可靠链路。
            log.warn("弹幕实时广播暂不可用，video={}", videoPublicId, e);
        }
    }

    private MessageListener subscribe(String videoPublicId) {
        MessageListener listener = (message, pattern) -> onMessage(videoPublicId, message);
        container.addMessageListener(listener, new ChannelTopic(channel(videoPublicId)));
        return listener;
    }

    private void onMessage(String videoPublicId, Message message) {
        Set<WebSocketSession> sessions = rooms.get(videoPublicId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        String body = new String(message.getBody(), java.nio.charset.StandardCharsets.UTF_8);
        sessions.removeIf(session -> !session.isOpen());
        if (sessions.isEmpty()) {
            unregister(videoPublicId, null);
            return;
        }
        for (WebSocketSession session : sessions) {
            try {
                synchronized (session) {
                    session.sendMessage(new TextMessage(body));
                }
            } catch (IOException e) {
                log.debug("弹幕 WebSocket 推送失败，session={}", session.getId(), e);
                try {
                    session.close();
                } catch (IOException ignored) {
                    // ignore close failure
                }
                unregister(videoPublicId, session);
            }
        }
    }

    private String channel(String videoPublicId) {
        return DanmakuCacheConstants.REALTIME_CHANNEL_PREFIX + videoPublicId;
    }
}
