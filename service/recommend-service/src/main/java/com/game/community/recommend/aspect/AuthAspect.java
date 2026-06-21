package com.game.community.recommend.aspect;

import com.game.community.common.constant.user.UserConstants;
import com.game.community.model.base.PageResult;
import com.game.community.model.base.Result;
import com.game.community.utils.ThreadLocal.UserThreadLocal;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

@Slf4j
@Aspect
@Component
public class AuthAspect {

    @Around("@annotation(com.game.community.common.annotation.LoginCheck)")
    public Object aroundLoginCheck(ProceedingJoinPoint joinPoint) throws Throwable {
        if (UserThreadLocal.getUserId() == null) {
            return errorForReturnType(joinPoint, "请先登录");
        }
        return joinPoint.proceed();
    }

    @Around("@annotation(com.game.community.common.annotation.AdminCheck)")
    public Object aroundAdminCheck(ProceedingJoinPoint joinPoint) throws Throwable {
        Long userId = UserThreadLocal.getUserId();
        Integer type = UserThreadLocal.getType();
        if (userId == null) {
            return errorForReturnType(joinPoint, "请先登录");
        }
        if (type == null || type != UserConstants.UserType.ADMIN) {
            log.warn("推荐服务管理员校验失败: userId={}, type={}", userId, type);
            return errorForReturnType(joinPoint, "无权限，需要管理员权限");
        }
        return joinPoint.proceed();
    }

    private Object errorForReturnType(ProceedingJoinPoint joinPoint, String message) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        if (PageResult.class.isAssignableFrom(signature.getReturnType())) {
            PageResult<Object> result = new PageResult<>();
            result.setCode(500);
            result.setMessage(message);
            result.setPage(1L);
            result.setSize(0L);
            result.setTotal(0L);
            return result;
        }
        return Result.error(message);
    }
}
