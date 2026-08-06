package com.game.community.content.aspect;

import com.game.community.model.enums.user.AccountType;
import com.game.community.model.base.Result;
import com.game.community.utils.ThreadLocal.UserThreadLocal;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * 登录校验和管理员校验切面
 */
@Slf4j
@Aspect
@Component
public class AuthAspect {

    /**
     * 登录校验切面
     */
    @Around("@annotation(com.game.community.common.annotation.LoginCheck)")
    public Object aroundLoginCheck(ProceedingJoinPoint joinPoint) throws Throwable {
        // 检查用户ID是否存在
        Long userId = UserThreadLocal.getUserId();
        if (userId == null) {
            log.warn("登录校验失败: 用户未登录");
            return Result.error("请先登录");
        }
        return joinPoint.proceed();
    }

    /**
     * 管理员校验切面
     */
    @Around("@annotation(com.game.community.common.annotation.AdminCheck)")
    public Object aroundAdminCheck(ProceedingJoinPoint joinPoint) throws Throwable {
        // 1. 登录校验
        Long userId = UserThreadLocal.getUserId();
        if (userId == null) {
            log.warn("管理员校验失败: 用户未登录");
            return Result.error("请先登录");
        }

        // 2. 管理员权限校验
        Integer userType = UserThreadLocal.getType();
        if (userType == null || userType != AccountType.ADMIN.getCode()) {
            log.warn("管理员校验失败: 用户不是管理员, userId={}, type={}", userId, userType);
            return Result.error("无权限，需要管理员权限");
        }

        return joinPoint.proceed();
    }
}
