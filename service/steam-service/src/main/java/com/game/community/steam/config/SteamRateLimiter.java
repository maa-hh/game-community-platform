package com.game.community.steam.config;

import com.game.community.common.constant.steam.SteamRedisConstants;
import com.game.community.utils.RedisUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Steam 集群级出站请求限流器。
 *
 * <p>Redis Lua 脚本为每个请求预定一个时间槽，使用 Redis 服务端时间避免多实例本地时钟不一致。
 * 该组件只负责请求开始频率，429 重试由 RestTemplate 拦截器负责。</p>
 */
@Component
@RequiredArgsConstructor
public class SteamRateLimiter {

    private final RedisUtils redisUtils;
    private final SteamProperties steamProperties;

    /** 等待集群共享的 Steam 请求时间槽。 */
    public void awaitNextRequest() throws IOException {
        long waitMs;
        try {
            waitMs = redisUtils.reserveRateLimitSlot(
                    SteamRedisConstants.API_RATE_LIMIT_SLOT_KEY,
                    steamProperties.getRequestIntervalMs());
        } catch (RuntimeException e) {
            // 限流状态不可确认时默认阻断请求，避免 Redis 故障导致 Steam 流量失控。
            throw new IOException("Redis Steam 限流状态不可用", e);
        }
        if (waitMs <= 0L) {
            return;
        }
        try {
            Thread.sleep(waitMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("等待 Steam 限流时间槽被中断", e);
        }
    }
}
