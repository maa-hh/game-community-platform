package com.game.community.ai.resilience;

import com.game.community.ai.agent.AgentModelRegistry;
import com.game.community.ai.config.AgentModelProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

/** 基于 Resilience4j 的 Provider 级限速、并发隔离和错误熔断。 */
@Slf4j
@Component
public class Resilience4jProviderResilienceGuard implements ProviderResilienceGuard {

    private final Map<String, ProviderState> states = new ConcurrentHashMap<>();

    /** 获取 Provider 独立许可后执行调用，拒绝请求不进入供应商网络。 */
    @Override
    public <T> T execute(AgentModelRegistry.ModelClient model, Supplier<T> action) {
        ProviderState state = states.computeIfAbsent(model.provider(),
                ignored -> new ProviderState(model.configuration()));
        if (!state.rateLimiter.acquirePermission()) {
            throw new IllegalStateException("provider rate limit reached: " + model.provider());
        }
        if (!state.bulkhead.tryAcquire()) {
            throw new IllegalStateException("provider concurrency limit reached: " + model.provider());
        }
        try {
            return state.circuitBreaker.executeSupplier(action);
        } finally {
            state.bulkhead.release();
        }
    }

    /** 配置刷新后重建 Provider 保护器，避免沿用旧配额或熔断窗口。 */
    @EventListener(EnvironmentChangeEvent.class)
    public void onEnvironmentChange(EnvironmentChangeEvent event) {
        if (event.getKeys().stream().anyMatch(key -> key.startsWith("ai-agent.models."))) {
            states.clear();
            log.info("AI Provider Resilience4j 配置已刷新");
        }
    }

    /** 为单个 Provider 固化独立的速率、并发和熔断参数。 */
    private static final class ProviderState {

        private final RateLimiter rateLimiter;
        private final Semaphore bulkhead;
        private final CircuitBreaker circuitBreaker;

        private ProviderState(AgentModelProperties.Provider provider) {
            this.rateLimiter = RateLimiter.of("provider-rate-limit", RateLimiterConfig.custom()
                    .limitRefreshPeriod(Duration.ofSeconds(1))
                    .limitForPeriod(Math.max(1, provider.getRequestsPerSecond()))
                    .timeoutDuration(Duration.ZERO)
                    .build());
            this.bulkhead = new Semaphore(Math.max(1, provider.getMaxConcurrentCalls()));
            this.circuitBreaker = CircuitBreaker.of("provider-circuit-breaker", CircuitBreakerConfig.custom()
                    .failureRateThreshold(provider.getCircuitFailureRateThreshold())
                    .minimumNumberOfCalls(Math.max(2, provider.getCircuitMinimumNumberOfCalls()))
                    .slidingWindowSize(Math.max(2, provider.getCircuitSlidingWindowSize()))
                    .waitDurationInOpenState(Duration.ofSeconds(Math.max(1, provider.getCircuitOpenSeconds())))
                    .permittedNumberOfCallsInHalfOpenState(1)
                    .build());
        }
    }
}
