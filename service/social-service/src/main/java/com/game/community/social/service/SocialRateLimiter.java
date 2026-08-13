package com.game.community.social.service;

import com.game.community.common.exception.BusinessException;
import com.game.community.common.constant.social.SocialRateLimitConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 社交行为的用户级业务限额。
 *
 * <p>Sentinel 负责服务容量保护，这里只负责跨实例共享的用户行为配额，避免把 Redis
 * 细节泄漏到 Controller 或业务 Service。</p>
 */
@Component
@RequiredArgsConstructor
public class SocialRateLimiter {

    private static final DefaultRedisScript<Long> INCREMENT_SCRIPT = new DefaultRedisScript<>(
            "local current = redis.call('INCR', KEYS[1]); "
                    + "if current == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]); end; "
                    + "return current;", Long.class);

    private final StringRedisTemplate stringRedisTemplate;

    @Value("${" + SocialRateLimitConstants.ENABLED_PROPERTY + "}")
    private boolean enabled;

    @Value("${" + SocialRateLimitConstants.FAIL_OPEN_PROPERTY + "}")
    private boolean failOpen;

    /**
     * 在一个固定窗口内判断用户是否还能执行指定社交动作。
     *
     * @param userId 当前用户的内部标识，只在服务内部使用
     * @param action 社交动作标识
     * @param limit 窗口内允许的次数
     * @param windowSeconds 窗口时长（秒）
     * @return 未超限返回 {@code true}
     */
    public boolean allow(Long userId, String action, int limit, int windowSeconds) {
        if (!enabled || userId == null) {
            return true;
        }
        String key = SocialRateLimitConstants.REDIS_KEY_PREFIX + action + ":" + userId + ":"
                + (System.currentTimeMillis() / (windowSeconds * 1000L));
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
