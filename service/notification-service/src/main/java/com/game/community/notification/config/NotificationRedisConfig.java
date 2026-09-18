package com.game.community.notification.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.community.common.constant.notification.NotificationConstants;
import com.game.community.notification.service.impl.SseServiceImpl;
import com.game.community.notification.sse.NotificationSseBroadcast;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Slf4j
@Configuration
public class NotificationRedisConfig {

    @Bean
    public RedisMessageListenerContainer notificationRedisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            ObjectMapper objectMapper,
            SseServiceImpl sseService) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener((message, pattern) -> {
            try {
                NotificationSseBroadcast broadcast = objectMapper.readValue(
                        message.getBody(), NotificationSseBroadcast.class);
                sseService.sendLocal(broadcast);
            } catch (Exception e) {
                log.warn("解析通知 SSE 广播失败", e);
            }
        }, new ChannelTopic(NotificationConstants.RedisChannel.SSE_BROADCAST));
        return container;
    }
}
