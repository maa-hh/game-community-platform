package com.game.community.utils.session;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.community.common.constant.user.RedisConstants;
import com.game.community.model.vo.user.UserSessionVO;
import com.game.community.utils.RedisUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 用户会话 Redis 读取（网关 / user-service 共用）。
 * <p>
 * 鉴权热路径通过 MGET 一次读取 session 与 session-active 标记。
 */
@Component
@RequiredArgsConstructor
public class UserSessionRedisReader {

    private final RedisUtils redisUtils;
    private final ObjectMapper objectMapper;

    /**
     * 读取仍为活跃登录态的会话；已被踢下线或会话过期时返回 null。
     */
    public UserSessionVO loadActiveSession(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return null;
        }
        List<String> values = redisUtils.multiGet(
                RedisConstants.SESSION_PREFIX + sessionId,
                RedisConstants.SESSION_ACTIVE_PREFIX + sessionId);
        String sessionText = values.get(0);
        String activeFlag = values.get(1);
        if (!StringUtils.hasText(sessionText) || !StringUtils.hasText(activeFlag)) {
            return null;
        }
        return parseSession(sessionText, sessionId);
    }

    public UserSessionVO loadSession(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return null;
        }
        String sessionText = redisUtils.get(RedisConstants.SESSION_PREFIX + sessionId);
        if (!StringUtils.hasText(sessionText)) {
            return null;
        }
        return parseSession(sessionText, sessionId);
    }

    private UserSessionVO parseSession(String sessionText, String sessionId) {
        try {
            return objectMapper.readValue(sessionText, UserSessionVO.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("会话数据异常: sessionId=" + sessionId, e);
        }
    }
}
