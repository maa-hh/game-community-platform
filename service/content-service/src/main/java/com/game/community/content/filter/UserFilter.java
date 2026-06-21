package com.game.community.content.filter;

import com.game.community.common.constant.gateway.GatewayConstants;
import com.game.community.model.ThreadLocal.UserContex;
import com.game.community.utils.ThreadLocal.UserThreadLocal;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 用户过滤器 - 从header中获取用户ID并存入ThreadLocal
 */
@Slf4j
@Order(1)
@Component
public class UserFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String userIdStr = httpRequest.getHeader(GatewayConstants.USER_ID_HEADER);
        String userTypeStr = httpRequest.getHeader(GatewayConstants.USER_TYPE_HEADER);
        String gameAccountStr = httpRequest.getHeader(GatewayConstants.GAME_ACCOUNT_HEADER);
        String sessionId = httpRequest.getHeader(GatewayConstants.SESSION_ID_HEADER);

        if (userIdStr != null && !userIdStr.isBlank()) {
            try {
                Long userId = Long.parseLong(userIdStr);
                Integer userType = userTypeStr == null || userTypeStr.isBlank() ? 0 : Integer.parseInt(userTypeStr);
                String gameAccount = gameAccountStr == null ? "" : gameAccountStr;
                UserContex userContex = new UserContex(userId, userType, gameAccount, sessionId);
                UserThreadLocal.setUser(userContex);
                log.debug("设置用户到ThreadLocal: userId={}, type={}, gameAccount={}, sessionId={}", userId, userType, gameAccount, sessionId);
            } catch (NumberFormatException e) {
                log.warn("无效的用户上下文: userId={}, type={}", userIdStr, userTypeStr);
            }
        }
        try {
            chain.doFilter(request, response);
        } finally {
            UserThreadLocal.removeUser();
        }
    }
}
