package com.game.community.user;

import com.game.community.model.ThreadLocal.UserContex;
import com.game.community.model.base.Result;
import com.game.community.user.aspect.AuthAspect;
import com.game.community.utils.ThreadLocal.UserThreadLocal;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 登录和管理员校验切面测试
 */
class AuthAspectTest {

    private final AuthAspect authAspect = new AuthAspect();

    @AfterEach
    void tearDown() {
        UserThreadLocal.removeUser();
    }

    @Test
    void loginCheckShouldRejectAnonymousUser() throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);

        Object result = authAspect.aroundLoginCheck(joinPoint);

        assertThat(result).isInstanceOf(Result.class);
        assertThat(((Result<?>) result).getMessage()).isEqualTo("请先登录");
        verify(joinPoint, never()).proceed();
    }

    @Test
    void adminCheckShouldRejectNormalUser() throws Throwable {
        UserThreadLocal.setUser(new UserContex(1L, 0, ""));
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);

        Object result = authAspect.aroundAdminCheck(joinPoint);

        assertThat(result).isInstanceOf(Result.class);
        assertThat(((Result<?>) result).getMessage()).isEqualTo("无权限，需要管理员权限");
        verify(joinPoint, never()).proceed();
    }

    @Test
    void adminCheckShouldProceedForAdmin() throws Throwable {
        UserThreadLocal.setUser(new UserContex(1L, 1, ""));
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.proceed()).thenReturn("ok");

        Object result = authAspect.aroundAdminCheck(joinPoint);

        assertThat(result).isEqualTo("ok");
        verify(joinPoint).proceed();
    }
}
