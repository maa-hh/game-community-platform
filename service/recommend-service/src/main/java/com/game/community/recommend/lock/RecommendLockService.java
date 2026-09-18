package com.game.community.recommend.lock;

import com.game.community.utils.RedisUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 推荐服务维护任务的可续租分布式锁。
 *
 * <p>锁只用于跨实例协调，真正的实时增量保护由 Redis Lua 脚本在同一条命令中完成。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendLockService {

    private final RedisUtils redisUtils;
    private final ScheduledExecutorService recommendLockRenewalExecutor;

    /** 获取锁并在任务执行期间续租；未获取到锁时返回 false。 */
    public boolean tryExecute(String key, long leaseSeconds, Runnable action) {
        String token = UUID.randomUUID().toString();
        if (!Boolean.TRUE.equals(redisUtils.setIfAbsent(key, token, leaseSeconds))) {
            return false;
        }
        long renewalPeriodSeconds = Math.max(1L, leaseSeconds / 3L);
        ScheduledFuture<?> renewal = recommendLockRenewalExecutor.scheduleAtFixedRate(
                () -> {
                    if (!redisUtils.renewLock(key, token, leaseSeconds)) {
                        log.error("推荐服务维护锁续租失败，key={}", key);
                    }
                }, renewalPeriodSeconds, renewalPeriodSeconds, TimeUnit.SECONDS);
        try {
            action.run();
            return true;
        } finally {
            renewal.cancel(false);
            redisUtils.unlock(key, token);
        }
    }

    /** 判断维护任务是否正在执行，供 Kafka 实时消费在维护窗口内主动重试。 */
    public boolean isHeld(String key) {
        return redisUtils.get(key) != null;
    }
}
