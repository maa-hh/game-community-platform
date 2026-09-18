package com.game.community.ai.concurrency;

import com.game.community.ai.config.ModerationProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/** 使用 Redis Lua 脚本实现跨实例 Provider 并发许可。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisModelConcurrencyLimiter implements ModelConcurrencyLimiter {

    private static final String KEY_PREFIX = "ai-agent:moderation:concurrency:";

    private static final DefaultRedisScript<Long> ACQUIRE_SCRIPT = new DefaultRedisScript<>("""
            local current = tonumber(redis.call('GET', KEYS[1]) or '0')
            local limit = tonumber(ARGV[1])
            if current >= limit then
                return 0
            end
            redis.call('INCR', KEYS[1])
            redis.call('PEXPIRE', KEYS[1], ARGV[2])
            return 1
            """, Long.class);

    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>("""
            local current = tonumber(redis.call('GET', KEYS[1]) or '0')
            if current <= 1 then
                redis.call('DEL', KEYS[1])
            else
                redis.call('DECR', KEYS[1])
            end
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final ModerationProperties properties;

    /** 原子增加 Provider 计数，并为异常退出保留租约兜底。 */
    @Override
    public boolean tryAcquire(String provider) {
        try {
            Long result = redisTemplate.execute(ACQUIRE_SCRIPT,
                    List.of(key(provider)),
                    String.valueOf(Math.max(1, properties.getGlobalMaxConcurrentModelCalls())),
                    String.valueOf(Math.max(1000L, properties.getGlobalPermitLeaseSeconds() * 1000L)));
            return Long.valueOf(1L).equals(result);
        } catch (Exception exception) {
            log.error("AI 全局并发许可存储不可用，拒绝进入模型调用: provider={}", provider, exception);
            return false;
        }
    }

    /** 原子减少 Provider 计数，避免正常请求长期占用集群许可。 */
    @Override
    public void release(String provider) {
        try {
            redisTemplate.execute(RELEASE_SCRIPT, List.of(key(provider)));
        } catch (Exception exception) {
            log.warn("AI 全局并发许可释放失败，将由租约自动回收: provider={}", provider, exception);
        }
    }

    /** 生成不包含用户输入内容的稳定 Redis Key。 */
    private String key(String provider) {
        return KEY_PREFIX + (provider == null || provider.isBlank() ? "default" : provider.trim());
    }
}
