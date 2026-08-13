package com.game.community.social.aspect;

import com.game.community.common.exception.BusinessException;
import com.game.community.social.service.SocialRateLimiter;
import com.game.community.utils.ThreadLocal.UserThreadLocal;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * 统一拦截声明了 {@link SocialRateLimit} 的写接口。
 *
 * <p>切面只负责把协议配置转交给限流组件；Redis、失败策略等中间件细节留在
 * {@link SocialRateLimiter} 内部。</p>
 */
@Aspect
@Component
@RequiredArgsConstructor
public class SocialRateLimitAspect {

    private final SocialRateLimiter socialRateLimiter;

    /**
     * 在执行社交写操作前检查当前用户配额。
     */
    @Around("@annotation(rateLimit)")
    public Object limit(ProceedingJoinPoint joinPoint, SocialRateLimit rateLimit) throws Throwable {
        Long userId = UserThreadLocal.getUserId();
        if (!socialRateLimiter.allow(userId, rateLimit.action(), rateLimit.limit(), rateLimit.windowSeconds())) {
            throw new BusinessException("操作过于频繁，请稍后再试");
        }
        return joinPoint.proceed();
    }
}
