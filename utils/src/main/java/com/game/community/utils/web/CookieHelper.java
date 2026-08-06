package com.game.community.utils.web;

import com.game.community.common.constant.Constants;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Cookie 工具：读写 refresh token 的 HttpOnly Cookie（跨服务可复用）
 */
@Component
public class CookieHelper {

    @Value("${auth.cookie.secure:${AUTH_COOKIE_SECURE:false}}")
    private boolean cookieSecure;

    public void writeRefreshTokenCookie(HttpServletRequest request, HttpServletResponse response,
                                         String refreshToken, long maxAgeSeconds) {
        ResponseCookie cookie = ResponseCookie.from(Constants.REFRESH_TOKEN_COOKIE_NAME, refreshToken)
                .httpOnly(true)
                .secure(resolveSecure(request))
                .sameSite("Lax")
                .path(Constants.REFRESH_TOKEN_COOKIE_PATH)
                .maxAge(maxAgeSeconds)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public void clearRefreshTokenCookie(HttpServletRequest request, HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(Constants.REFRESH_TOKEN_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(resolveSecure(request))
                .sameSite("Lax")
                .path(Constants.REFRESH_TOKEN_COOKIE_PATH)
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public String extractRefreshToken(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        for (var cookie : request.getCookies()) {
            if (Constants.REFRESH_TOKEN_COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private boolean resolveSecure(HttpServletRequest request) {
        return cookieSecure || request.isSecure();
    }
}
