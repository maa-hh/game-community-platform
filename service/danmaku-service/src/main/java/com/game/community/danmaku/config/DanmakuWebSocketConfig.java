package com.game.community.danmaku.config;

import com.game.community.danmaku.websocket.DanmakuWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class DanmakuWebSocketConfig implements WebSocketConfigurer {

    private final DanmakuWebSocketHandler handler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/danmaku/ws/**")
                .setAllowedOriginPatterns("*");
    }
}
