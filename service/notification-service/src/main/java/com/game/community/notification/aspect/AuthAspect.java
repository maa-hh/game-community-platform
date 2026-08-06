package com.game.community.notification.aspect;

import com.game.community.common.annotation.AdminCheck;
import com.game.community.common.annotation.LoginCheck;
import com.game.community.model.enums.user.AccountType;
import com.game.community.model.base.Result;
import com.game.community.utils.ThreadLocal.UserThreadLocal;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Slf4j
@Aspect
@Component
public class AuthAspect {

    @Around("@annotation(loginCheck)")
    public Object aroundLoginCheck(ProceedingJoinPoint joinPoint, LoginCheck loginCheck) throws Throwable {
        if (UserThreadLocal.getUserId() == null) {
            return Result.error("请先登录");
        }
        return joinPoint.proceed();
    }

    @Around("@annotation(adminCheck)")
    public Object aroundAdminCheck(ProceedingJoinPoint joinPoint, AdminCheck adminCheck) throws Throwable {
        Long userId = UserThreadLocal.getUserId();
        Integer type = UserThreadLocal.getType();
        if (userId == null) {
            return Result.error("请先登录");
        }
        if (type == null || type != AccountType.ADMIN.getCode()) {
            log.warn("通知服务管理员校验失败: userId={}, type={}", userId, type);
            return Result.error("无权限，需要管理员权限");
        }
        return joinPoint.proceed();
    }
}
