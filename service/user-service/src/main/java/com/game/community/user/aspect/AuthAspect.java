package com.game.community.user.aspect;

import com.game.community.common.constant.user.UserConstants;
import com.game.community.model.base.Result;
import com.game.community.utils.ThreadLocal.UserThreadLocal;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * 登录态和管理员权限校验切面
 */
@Slf4j
@Aspect
@Component
public class AuthAspect {

    @Around("@annotation(com.game.community.common.annotation.LoginCheck)")
    public Object aroundLoginCheck(ProceedingJoinPoint joinPoint) throws Throwable {
        Long userId = UserThreadLocal.getUserId();
        if (userId == null) {
            log.warn("登录校验失败: 用户未登录");
            return Result.error("请先登录");
        }
        return joinPoint.proceed();
    }

    @Around("@annotation(com.game.community.common.annotation.AdminCheck)")
    public Object aroundAdminCheck(ProceedingJoinPoint joinPoint) throws Throwable {
        Long userId = UserThreadLocal.getUserId();
        if (userId == null) {
            log.warn("管理员校验失败: 用户未登录");
            return Result.error("请先登录");
        }
        Integer type = UserThreadLocal.getType();
        if (type == null || type != UserConstants.UserType.ADMIN) {
            log.warn("管理员校验失败: userId={}, type={}", userId, type);
            return Result.error("无权限，需要管理员权限");
        }
        return joinPoint.proceed();
    }
}
