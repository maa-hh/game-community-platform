package com.game.community.user;

import com.game.community.common.constant.gateway.GatewayConstants;
import com.game.community.user.filter.UserFilter;
import com.game.community.utils.ThreadLocal.UserThreadLocal;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 用户上下文过滤器测试
 */
class UserFilterTest {

    @Test
    void shouldSetAndClearThreadLocalFromHeaders() throws Exception {
        UserFilter filter = new UserFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader(GatewayConstants.USER_ID_HEADER, "10001");
        request.addHeader(GatewayConstants.USER_TYPE_HEADER, "1");
        request.addHeader(GatewayConstants.GAME_ACCOUNT_HEADER, "game_10001");
        request.addHeader(GatewayConstants.SESSION_ID_HEADER, "session-1");

        FilterChain chain = (servletRequest, servletResponse) -> {
            assertThat(UserThreadLocal.getUserId()).isEqualTo(10001L);
            assertThat(UserThreadLocal.getType()).isEqualTo(1);
            assertThat(UserThreadLocal.getGameAccount()).isEqualTo("game_10001");
            assertThat(UserThreadLocal.getSessionId()).isEqualTo("session-1");
        };

        filter.doFilter(request, response, chain);
        assertThat(UserThreadLocal.getUserId()).isNull();
    }
}
