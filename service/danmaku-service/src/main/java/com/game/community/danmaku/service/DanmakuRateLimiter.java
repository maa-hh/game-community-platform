package com.game.community.danmaku.service;

import com.game.community.common.exception.BusinessException;
import com.game.community.danmaku.common.constant.DanmakuCacheConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class DanmakuRateLimiter {

    private static final DefaultRedisScript<Long> SCRIPT = new DefaultRedisScript<>(
            "local current = redis.call('INCR', KEYS[1]); "
                    + "if current == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]); end; "
                    + "return current;", Long.class);

    private final StringRedisTemplate redis;

    @Value("${danmaku.user-rate-limit:6}")
    private int limit;

    @Value("${danmaku.user-rate-window-seconds:3}")
    private int windowSeconds;

    /** 按用户和视频执行原子发送频控，Redis 异常时拒绝本次发送。 */
    public void check(Long userId, String videoPublicId) {
        long safeWindowSeconds = Math.max(1, windowSeconds);
        String bucket = String.valueOf(System.currentTimeMillis() / (safeWindowSeconds * 1000L));
        String key = DanmakuCacheConstants.RATE_KEY_PREFIX + videoPublicId + ":" + userId + ":" + bucket;
        try {
            Long current = redis.execute(SCRIPT, List.of(key), String.valueOf(safeWindowSeconds));
            if (current == null || current > Math.max(1, limit)) {
                throw new BusinessException("弹幕发送过于频繁，请稍后再试");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new BusinessException("限流服务暂不可用，请稍后重试");
        }
    }
}
