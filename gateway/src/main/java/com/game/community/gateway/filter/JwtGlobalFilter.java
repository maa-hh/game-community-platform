package com.game.community.gateway.filter;

import com.game.community.common.constant.AuthErrorCodes;
import com.game.community.common.constant.Constants;
import com.game.community.common.constant.gateway.GatewayConstants;
import com.game.community.model.enums.user.SessionStatus;
import com.game.community.model.enums.user.UserAccountStatus;
import com.game.community.model.vo.user.UserSessionVO;
import com.game.community.utils.JwtUtils;
import com.game.community.utils.session.UserSessionRedisReader;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * JWT 全局过滤器：
 * - 认证白名单：直接放行
 * - 游客可读：无 token 放行；有 token 则校验并透传用户头（liked/favorited）
 * - 其余：必须登录
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtGlobalFilter implements GlobalFilter, Ordered {

    private final UserSessionRedisReader sessionRedisReader;

    @Value("${gateway.internal-secret}")
    private String internalSecret;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove(GatewayConstants.INTERNAL_SECRET_HEADER);
                    headers.add(GatewayConstants.INTERNAL_SECRET_HEADER, internalSecret);
                })
                .build();
        exchange = exchange.mutate().request(request).build();
        String path = request.getURI().getPath();
        String normalized = normalizePath(path);

        if (isAuthPublicPath(normalized)) {
            return chain.filter(exchange);
        }

        boolean publicRead = isPublicReadPath(normalized);
        String token = getToken(request);

        if (!StringUtils.hasText(token)) {
            if (publicRead) {
                return chain.filter(exchange);
            }
            return unauthorized(exchange.getResponse(), "请先登录");
        }

        try {
            return chain.filter(exchange.mutate().request(authenticate(request, token)).build());
        } catch (AuthFailException e) {
            if (publicRead) {
                // 坏 token 不阻断游客可读，按未登录继续
                log.warn("公开读路径 token 无效，降级游客: path={}, err={}", normalized, e.getMessage());
                return chain.filter(exchange);
            }
            return unauthorized(exchange.getResponse(), e.getMessage());
        } catch (Exception e) {
            log.warn("JWT校验失败: {}", e.getMessage());
            if (publicRead) {
                return chain.filter(exchange);
            }
            return unauthorized(exchange.getResponse(), "token验证失败");
        }
    }

    private ServerHttpRequest authenticate(ServerHttpRequest request, String token) {
        Claims claims = JwtUtils.parseToken(Constants.ACCESS_JWT_SECRET, token);
        if (!"access".equals(claims.get("tokenType", String.class))) {
            throw new AuthFailException("token无效");
        }
        Long accountId = getLongClaim(claims, "accountId");
        Integer userType = getIntegerClaim(claims, "type");
        String steamAccount = claims.get("steamAccount", String.class);
        String sessionId = claims.get("sessionId", String.class);
        if (accountId == null || !StringUtils.hasText(sessionId)) {
            throw new AuthFailException("token无效");
        }

        UserSessionVO session = sessionRedisReader.loadActiveSession(sessionId);
        if (session == null
                || session.getUserId() == null
                || session.getStatus() != SessionStatus.ONLINE) {
            throw new AuthFailException("token已失效");
        }
        Long userId = session.getUserId();
        if (session.getAccountId() != null && !accountId.equals(session.getAccountId())) {
            throw new AuthFailException("token无效");
        }

        if (session.getAccountStatus() != null
                && (session.getAccountStatus() == UserAccountStatus.BANNED
                || session.getAccountStatus() == UserAccountStatus.CANCELLED)) {
            throw new AuthFailException("账号状态异常");
        }

        ServerHttpRequest.Builder builder = request.mutate()
                .headers(headers -> {
                    headers.remove(GatewayConstants.USER_ID_HEADER);
                    headers.remove(GatewayConstants.USER_TYPE_HEADER);
                    headers.remove(GatewayConstants.STEAM_ACCOUNT_HEADER);
                    headers.remove(GatewayConstants.SESSION_ID_HEADER);
                })
                .header(GatewayConstants.USER_ID_HEADER, userId.toString())
                .header(GatewayConstants.USER_TYPE_HEADER, String.valueOf(userType == null ? 0 : userType))
                .header(GatewayConstants.SESSION_ID_HEADER, sessionId)
                .header(GatewayConstants.INTERNAL_SECRET_HEADER, internalSecret);
        if (StringUtils.hasText(steamAccount)) {
            builder.header(GatewayConstants.STEAM_ACCOUNT_HEADER, steamAccount);
        }
        return builder.build();
    }

    private String normalizePath(String path) {
        return path.startsWith("/api") ? path.substring(4) : path;
    }

    private boolean isAuthPublicPath(String normalized) {
        return GatewayConstants.AUTH_PUBLIC_PATH_PREFIXES.stream().anyMatch(normalized::startsWith);
    }

    private boolean isPublicReadPath(String normalized) {
        return GatewayConstants.PUBLIC_READ_PATH_PREFIXES.stream().anyMatch(normalized::startsWith);
    }

    private String getToken(ServerHttpRequest request) {
        String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(authorization) && authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }
        String queryToken = request.getQueryParams().getFirst("accessToken");
        if (StringUtils.hasText(queryToken)) {
            return queryToken;
        }
        return null;
    }

    private Long getLongClaim(Claims claims, String key) {
        Object value = claims.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            return Long.parseLong(text);
        }
        return null;
    }

    private Integer getIntegerClaim(Claims claims, String key) {
        Object value = claims.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            return Integer.parseInt(text);
        }
        return null;
    }

    private Mono<Void> unauthorized(ServerHttpResponse response, String message) {
        return unauthorized(response, AuthErrorCodes.ACCESS_EXPIRED, message);
    }

    private Mono<Void> unauthorized(ServerHttpResponse response, int code, String message) {
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"code\":" + code + ",\"message\":\"" + message + "\",\"data\":null}";
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -100;
    }

    private static final class AuthFailException extends RuntimeException {
        private AuthFailException(String message) {
            super(message);
        }
    }
}
