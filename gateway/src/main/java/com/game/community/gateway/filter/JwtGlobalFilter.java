package com.game.community.gateway.filter;

import com.game.community.common.constant.Constants;
import com.game.community.common.constant.gateway.GatewayConstants;
import com.game.community.common.constant.user.RedisConstants;
import com.game.community.model.vo.user.UserSessionVO;
import com.game.community.utils.JwtUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * JWT全局过滤器：白名单外统一校验 token，并透传用户上下文 Header。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtGlobalFilter implements GlobalFilter, Ordered {

    private static final List<String> WHITE_PATHS = List.of(
            "/user/sendCode",
            "/user/register",
            "/user/login",
            "/user/token/refresh"
    );

    private final StringRedisTemplate stringRedisTemplate;

    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        if (isWhitePath(path)) {
            return chain.filter(exchange);
        }

        String token = getToken(request);
        if (!StringUtils.hasText(token)) {
            return unauthorized(exchange.getResponse(), "请先登录");
        }

        try {
            Claims claims = JwtUtils.parseToken(Constants.ACCESS_JWT_SECRET, token);
            if (!"access".equals(claims.get("tokenType", String.class))) {
                return unauthorized(exchange.getResponse(), "token无效");
            }
            Long userId = getLongClaim(claims, "userId");
            Integer userType = getIntegerClaim(claims, "type");
            String gameAccount = claims.get("gameAccount", String.class);
            String sessionId = claims.get("sessionId", String.class);
            if (userId == null || !StringUtils.hasText(sessionId)) {
                return unauthorized(exchange.getResponse(), "token无效");
            }

            UserSessionVO session = getSession(sessionId);
            String activeSessionId = stringRedisTemplate.opsForValue().get(RedisConstants.ACTIVE_SESSION_PREFIX + userId);
            if (session == null
                    || session.getUserId() == null
                    || !userId.equals(session.getUserId())
                    || !sessionId.equals(activeSessionId)
                    || !"ONLINE".equals(session.getStatus())) {
                return unauthorized(exchange.getResponse(), "token已失效");
            }

            ServerHttpRequest.Builder builder = request.mutate()
                    .headers(headers -> {
                        headers.remove(GatewayConstants.USER_ID_HEADER);
                        headers.remove(GatewayConstants.USER_TYPE_HEADER);
                        headers.remove(GatewayConstants.GAME_ACCOUNT_HEADER);
                        headers.remove(GatewayConstants.SESSION_ID_HEADER);
                    })
                    .header(GatewayConstants.USER_ID_HEADER, userId.toString())
                    .header(GatewayConstants.USER_TYPE_HEADER, String.valueOf(userType == null ? 0 : userType))
                    .header(GatewayConstants.SESSION_ID_HEADER, sessionId);
            if (StringUtils.hasText(gameAccount)) {
                builder.header(GatewayConstants.GAME_ACCOUNT_HEADER, gameAccount);
            }

            return chain.filter(exchange.mutate().request(builder.build()).build());
        } catch (Exception e) {
            log.warn("JWT校验失败: {}", e.getMessage());
            return unauthorized(exchange.getResponse(), "token验证失败");
        }
    }

    private boolean isWhitePath(String path) {
        return WHITE_PATHS.stream().anyMatch(path::startsWith);
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

    private UserSessionVO getSession(String sessionId) {
        String sessionText = stringRedisTemplate.opsForValue().get(RedisConstants.SESSION_PREFIX + sessionId);
        if (!StringUtils.hasText(sessionText)) {
            return null;
        }
        try {
            return objectMapper.readValue(sessionText, UserSessionVO.class);
        } catch (Exception e) {
            log.warn("会话解析失败: sessionId={}, error={}", sessionId, e.getMessage());
            return null;
        }
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
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"code\":401,\"message\":\"" + message + "\",\"data\":null}";
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
