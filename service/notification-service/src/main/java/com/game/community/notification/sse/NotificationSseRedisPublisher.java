package com.game.community.notification.sse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.community.common.constant.notification.NotificationConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 通知 SSE 的 Redis 发布适配器；隔离 Redis 客户端，避免领域服务直接依赖中间件类型。
 */
@Component
@RequiredArgsConstructor
public class NotificationSseRedisPublisher {

    private final StringRedisTemplate redisTemplate;

    private final ObjectMapper objectMapper;

    /** 将跨实例 SSE 广播序列化后发布到共享频道。 */
    public void publish(NotificationSseBroadcast broadcast) throws JsonProcessingException {
        redisTemplate.convertAndSend(
                NotificationConstants.RedisChannel.SSE_BROADCAST,
                objectMapper.writeValueAsString(broadcast));
    }
}
