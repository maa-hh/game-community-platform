package com.game.community.danmaku.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.game.community.common.exception.BusinessException;
import com.game.community.danmaku.service.DanmakuArticleValidator;
import com.game.community.danmaku.service.DanmakuCommandService;
import com.game.community.danmaku.service.DanmakuRealtimeService;
import com.game.community.model.dto.danmaku.SendDanmakuDTO;
import com.game.community.model.vo.danmaku.DanmakuVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class DanmakuWebSocketHandler extends TextWebSocketHandler {

    private static final String VIDEO_ATTRIBUTE = "danmaku.videoPublicId";

    private final ObjectMapper objectMapper;
    private final DanmakuArticleValidator articleValidator;
    private final DanmakuCommandService commandService;
    private final DanmakuRealtimeService realtimeService;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String videoPublicId = resolveVideoPublicId(session);
        articleValidator.requireVideo(videoPublicId);
        session.getAttributes().put(VIDEO_ATTRIBUTE, videoPublicId);
        realtimeService.register(videoPublicId, session);
        send(session, connected(videoPublicId));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode root;
        try {
            root = objectMapper.readTree(message.getPayload());
        } catch (Exception e) {
            sendError(session, null, "弹幕请求格式无效");
            return;
        }
        String type = root.path("type").asText("");
        if ("ping".equals(type)) {
            send(session, simple("pong"));
            return;
        }
        if (!"send".equals(type)) {
            sendError(session, root.path("clientMessageId").asText(null), "不支持的弹幕操作");
            return;
        }

        String clientMessageId = root.path("clientMessageId").asText(null);
        Long userId = resolveUserId(session);
        if (userId == null) {
            sendError(session, clientMessageId, "请先登录后发送弹幕");
            return;
        }
        try {
            SendDanmakuDTO dto = objectMapper.treeToValue(root, SendDanmakuDTO.class);
            DanmakuVO result = commandService.accept(videoPublicId(session), userId, dto);
            ObjectNode ack = objectMapper.createObjectNode();
            ack.put("type", "send_ack");
            ack.put("clientMessageId", result.getClientMessageId());
            ack.set("data", objectMapper.valueToTree(result));
            send(session, ack);
        } catch (BusinessException e) {
            sendError(session, clientMessageId, e.getMessage());
        } catch (Exception e) {
            log.warn("处理弹幕发送失败", e);
            sendError(session, clientMessageId, "弹幕发送失败，请稍后重试");
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        unregister(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.debug("弹幕 WebSocket 传输异常，session={}", session.getId(), exception);
        unregister(session);
        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    private void unregister(WebSocketSession session) {
        String videoPublicId = videoPublicId(session);
        if (StringUtils.hasText(videoPublicId)) {
            realtimeService.unregister(videoPublicId, session);
        }
    }

    private String videoPublicId(WebSocketSession session) {
        Object value = session.getAttributes().get(VIDEO_ATTRIBUTE);
        return value == null ? resolveVideoPublicId(session) : String.valueOf(value);
    }

    private String resolveVideoPublicId(WebSocketSession session) {
        String path = session.getUri() == null ? "" : session.getUri().getPath();
        String[] segments = path.split("/");
        if (segments.length == 0 || !StringUtils.hasText(segments[segments.length - 1])) {
            throw new BusinessException("视频标识不能为空");
        }
        return URLDecoder.decode(segments[segments.length - 1], StandardCharsets.UTF_8);
    }

    private Long resolveUserId(WebSocketSession session) {
        String raw = session.getHandshakeHeaders().getFirst("X-User-Id");
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return Long.valueOf(raw);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private ObjectNode connected(String videoPublicId) {
        ObjectNode node = simple("connected");
        node.put("videoPublicId", videoPublicId);
        node.put("serverTime", System.currentTimeMillis());
        return node;
    }

    private ObjectNode simple(String type) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", type);
        return node;
    }

    private void sendError(WebSocketSession session, String clientMessageId, String error) throws IOException {
        ObjectNode node = simple("send_error");
        if (clientMessageId != null) {
            node.put("clientMessageId", clientMessageId);
        }
        node.put("code", 400);
        node.put("message", StringUtils.hasText(error) ? error : "弹幕发送失败");
        send(session, node);
    }

    private void send(WebSocketSession session, JsonNode node) throws IOException {
        if (!session.isOpen()) {
            return;
        }
        synchronized (session) {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(node)));
        }
    }
}
