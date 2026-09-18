package com.game.community.ai.aspect;

import com.game.community.common.annotation.AdminCheck;
import com.game.community.common.annotation.LoginCheck;
import com.game.community.ai.context.AiUserContextHolder;
import com.game.community.model.enums.user.AccountType;
import com.game.community.model.base.PageResult;
import com.game.community.model.base.Result;
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

    /** 校验接口是否存在网关注入的登录用户。 */
    @Around("@annotation(loginCheck)")
    public Object aroundLoginCheck(ProceedingJoinPoint joinPoint, LoginCheck loginCheck) throws Throwable {
        if (AiUserContextHolder.getUserId() == null) {
            return errorForReturnType(joinPoint, "请先登录");
        }
        return joinPoint.proceed();
    }

    /** 校验接口调用者是否为管理员。 */
    @Around("@annotation(adminCheck)")
    public Object aroundAdminCheck(ProceedingJoinPoint joinPoint, AdminCheck adminCheck) throws Throwable {
        Long userId = AiUserContextHolder.getUserId();
        Integer type = AiUserContextHolder.getType();
        if (userId == null) {
            return errorForReturnType(joinPoint, "请先登录");
        }
        if (type == null || type != AccountType.ADMIN.getCode()) {
            log.warn("AI 服务管理员校验失败: userId={}, type={}", userId, type);
            return errorForReturnType(joinPoint, "无权限，需要管理员权限");
        }
        return joinPoint.proceed();
    }

    /** 按目标方法返回类型构造统一的鉴权失败响应。 */
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
