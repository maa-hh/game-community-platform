package com.game.community.notification.filter;

import com.game.community.common.constant.gateway.GatewayConstants;
import com.game.community.model.ThreadLocal.UserContex;
import com.game.community.utils.ThreadLocal.UserThreadLocal;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

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
        String steamAccount = httpRequest.getHeader(GatewayConstants.STEAM_ACCOUNT_HEADER);
        String sessionId = httpRequest.getHeader(GatewayConstants.SESSION_ID_HEADER);
        if (userIdStr != null && !userIdStr.isBlank()) {
            try {
                Long userId = Long.parseLong(userIdStr);
                Integer userType = userTypeStr == null || userTypeStr.isBlank() ? 0 : Integer.parseInt(userTypeStr);
                UserThreadLocal.setUser(new UserContex(userId, userType, steamAccount == null ? "" : steamAccount, sessionId));
            } catch (NumberFormatException e) {
                log.warn("通知服务用户上下文解析失败: userId={}, type={}", userIdStr, userTypeStr);
            }
        }
        try {
            chain.doFilter(request, response);
        } finally {
            UserThreadLocal.removeUser();
        }
    }
}
