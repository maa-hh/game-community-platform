package com.game.community.social.filter;

import com.game.community.common.constant.social.SocialConstants;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 校验内容服务投递 Feed 的内部凭证。
 *
 * <p>凭证属于服务间通信基础设施，不进入 Controller 方法签名和业务 Service。</p>
 */
@Order(2)
@Component
public class InternalTokenFilter implements Filter {

    @Value("${social.internal-token:}")
    private String expectedToken;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        if (!SocialConstants.FEED_PUBLISH_PATH.equals(httpRequest.getRequestURI())
                || valid(httpRequest.getHeader(SocialConstants.INTERNAL_TOKEN_HEADER))) {
            chain.doFilter(request, response);
            return;
        }
        ((HttpServletResponse) response).sendError(HttpServletResponse.SC_UNAUTHORIZED, "内部调用未授权");
    }

    private boolean valid(String actualToken) {
        if (expectedToken == null || expectedToken.isBlank() || actualToken == null || actualToken.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(expectedToken.getBytes(StandardCharsets.UTF_8),
                actualToken.getBytes(StandardCharsets.UTF_8));
    }
}
