package com.game.community.ai.filter;

import com.game.community.ai.context.AiUserContextHolder;
import com.game.community.common.constant.gateway.GatewayConstants;
import com.game.community.model.ThreadLocal.UserContex;
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

/** 读取网关用户头并建立 AI 服务本地请求上下文。 */
@Slf4j
@Order(1)
@Component
public class UserFilter implements Filter {

    /** 从网关请求头构造用户上下文，并在请求完成后清理线程变量。 */
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
                AiUserContextHolder.set(new UserContex(userId, userType,
                        steamAccount == null ? "" : steamAccount, sessionId));
            } catch (NumberFormatException e) {
                log.warn("AI 服务用户上下文解析失败: userId={}, type={}", userIdStr, userTypeStr);
            }
        }
        try {
            chain.doFilter(request, response);
        } finally {
            AiUserContextHolder.clear();
        }
    }
}
