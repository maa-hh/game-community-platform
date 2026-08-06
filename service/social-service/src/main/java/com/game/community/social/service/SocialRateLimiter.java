package com.game.community.social.service;

import com.game.community.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class SocialRateLimiter {

    private static final DefaultRedisScript<Long> INCREMENT_SCRIPT = new DefaultRedisScript<>(
            "local current = redis.call('INCR', KEYS[1]); "
                    + "if current == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]); end; "
                    + "return current;", Long.class);

    private final StringRedisTemplate stringRedisTemplate;

    @Value("${social.rate-limit.enabled:true}")
    private boolean enabled;

    @Value("${social.rate-limit.fail-open:false}")
    private boolean failOpen;

    public boolean allow(Long userId, String action, int limit, int windowSeconds) {
        if (!enabled || userId == null) {
            return true;
        }
        String key = "social:rate:" + action + ":" + userId + ":" + (System.currentTimeMillis() / (windowSeconds * 1000L));
        try {
            Long count = stringRedisTemplate.execute(INCREMENT_SCRIPT,
                    List.of(key), String.valueOf(windowSeconds));
            return count != null && count <= limit;
        } catch (RuntimeException e) {
            if (failOpen) {
                return true;
            }
            throw new BusinessException("限流服务暂不可用，请稍后重试");
        }
    }
}
