package com.game.community.user.aspect;

import com.game.community.common.constant.ApiErrorCodes;
import com.game.community.model.base.Result;
import com.game.community.model.enums.user.AccountType;
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

    /** 执行 aroundLoginCheck 对应的业务处理。 */
    @Around("@annotation(com.game.community.common.annotation.LoginCheck)")
    public Object aroundLoginCheck(ProceedingJoinPoint joinPoint) throws Throwable {
        Long userId = UserThreadLocal.getUserId();
        if (userId == null) {
            log.warn("登录校验失败: 用户未登录");
            return Result.error(ApiErrorCodes.UNAUTHORIZED, "请先登录");
        }
        return joinPoint.proceed();
    }

    /** 执行 aroundAdminCheck 对应的业务处理。 */
    @Around("@annotation(com.game.community.common.annotation.AdminCheck)")
    public Object aroundAdminCheck(ProceedingJoinPoint joinPoint) throws Throwable {
        Long userId = UserThreadLocal.getUserId();
        if (userId == null) {
            log.warn("管理员校验失败: 用户未登录");
            return Result.error(ApiErrorCodes.UNAUTHORIZED, "请先登录");
        }
        Integer type = UserThreadLocal.getType();
        if (type == null || type != AccountType.ADMIN.getCode()) {
            log.warn("管理员校验失败: userId={}, type={}", userId, type);
            return Result.error(ApiErrorCodes.FORBIDDEN, "无权限，需要管理员权限");
        }
        return joinPoint.proceed();
    }
}
