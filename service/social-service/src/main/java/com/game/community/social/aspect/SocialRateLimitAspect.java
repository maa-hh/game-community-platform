package com.game.community.social.aspect;

import com.game.community.common.exception.BusinessException;
import com.game.community.social.service.SocialRateLimiter;
import com.game.community.utils.ThreadLocal.UserThreadLocal;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
@RequiredArgsConstructor
public class SocialRateLimitAspect {

    private final SocialRateLimiter socialRateLimiter;

    @Around("@annotation(rateLimit)")
    public Object limit(ProceedingJoinPoint joinPoint, SocialRateLimit rateLimit) throws Throwable {
        Long userId = UserThreadLocal.getUserId();
        if (!socialRateLimiter.allow(userId, rateLimit.action(), rateLimit.limit(), rateLimit.windowSeconds())) {
            throw new BusinessException("操作过于频繁，请稍后再试");
        }
        return joinPoint.proceed();
    }
}
