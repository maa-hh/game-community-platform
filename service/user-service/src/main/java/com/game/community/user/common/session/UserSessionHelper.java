package com.game.community.user.common.session;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.community.common.constant.Constants;
import com.game.community.common.constant.ApiErrorCodes;
import com.game.community.common.constant.user.RedisConstants;
import com.game.community.common.constant.user.UserSessionConstants;
import com.game.community.common.constant.user.UserConstants;
import com.game.community.common.exception.BusinessException;
import com.game.community.model.entity.user.User;
import com.game.community.model.entity.user.UserAccount;
import com.game.community.model.enums.user.SessionStatus;
import com.game.community.model.enums.user.UserStrings;
import com.game.community.model.vo.user.LoginUserVO;
import com.game.community.model.vo.user.LoginVO;
import com.game.community.model.vo.user.UserSessionVO;
import com.game.community.utils.JwtUtils;
import com.game.community.utils.RedisUtils;
import com.game.community.utils.session.UserSessionRedisReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 用户会话与令牌组件（本服务领域能力，非通用工具）
 * <p>
 * 会话读写、双令牌生成、令牌哈希；供认证 / 账户等业务 Service 共用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserSessionHelper {
    private final RedisUtils redisUtils;
    private final ObjectMapper objectMapper;
    private final UserSessionRedisReader sessionRedisReader;

    /**
     * 构建登录响应：创建会话 + 生成双令牌
     */
    public LoginVO buildLoginVO(User user, UserAccount account, String sessionId) {
        // 先保存带 refresh 哈希的 Redis 会话，再把双令牌返回给认证流程。
        String refreshToken = generateRefreshToken(sessionId);

        UserSessionVO session = new UserSessionVO();
        session.setUserId(user.getId());
        session.setAccountId(user.getAccountId());
        session.setType(account.getType());
        session.setSteamAccount(UserStrings.orEmpty(user.getSteamAccount()));
        session.setAccountStatus(account.getStatus());
        session.setRefreshTokenHash(hashToken(refreshToken));
        session.setStatus(SessionStatus.ONLINE);
        session.setLoginTime(LocalDateTime.now());
        session.setLastRefreshTime(LocalDateTime.now());
        saveSession(sessionId, user.getId(), session);

        String accessToken = generateAccessToken(user, account, sessionId);

        LoginVO vo = new LoginVO();
        vo.setAccessToken(accessToken);
        vo.setAccessExpiresIn(Constants.ACCESS_TOKEN_EXPIRE_TIME);
        vo.setRefreshToken(refreshToken);

        LoginUserVO loginUser = new LoginUserVO();
        BeanUtils.copyProperties(user, loginUser);
        vo.setUser(loginUser);
        return vo;
    }

    /** 执行 generateAccessToken 对应的业务处理。 */
    public String generateAccessToken(User user, UserAccount account, String sessionId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(UserSessionConstants.CLAIM_ACCOUNT_ID, user.getAccountId());
        claims.put(UserSessionConstants.CLAIM_ACCOUNT_TYPE, account.getType().getCode());
        claims.put(UserSessionConstants.CLAIM_STEAM_ACCOUNT, UserStrings.orEmpty(user.getSteamAccount()));
        claims.put(UserSessionConstants.CLAIM_SESSION_ID, sessionId);
        claims.put(UserSessionConstants.CLAIM_TOKEN_TYPE, UserSessionConstants.TOKEN_TYPE_ACCESS);
        return JwtUtils.generateToken(Constants.ACCESS_JWT_SECRET, claims, Constants.ACCESS_TOKEN_EXPIRE_TIME * 1000);
    }

    /** 执行 generateRefreshToken 对应的业务处理。 */
    public String generateRefreshToken(String sessionId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(UserSessionConstants.CLAIM_SESSION_ID, sessionId);
        claims.put(UserSessionConstants.CLAIM_TOKEN_TYPE, UserSessionConstants.TOKEN_TYPE_REFRESH);
        return JwtUtils.generateToken(Constants.REFRESH_JWT_SECRET, claims, Constants.REFRESH_TOKEN_EXPIRE_TIME * 1000);
    }

    /** 执行 saveSession 对应的业务处理。 */
    public void saveSession(String sessionId, Long userId, UserSessionVO session) {
        // 三个 Redis 索引一起写入，保证按 sessionId 和 userId 都能找到当前会话。
        boolean saved = redisUtils.saveSession(
                RedisConstants.SESSION_PREFIX + sessionId,
                RedisConstants.SESSION_ACTIVE_PREFIX + sessionId,
                RedisConstants.ACTIVE_SESSION_PREFIX + userId,
                writeSession(session),
                sessionId,
                Constants.REFRESH_TOKEN_EXPIRE_TIME);
        if (!saved) {
            throw new BusinessException("会话保存失败");
        }
    }

    /** 鉴权热路径：MGET session + session-active，一次往返 */
    public UserSessionVO loadSessionForAuth(String sessionId) {
        try {
            return sessionRedisReader.loadActiveSession(sessionId);
        } catch (IllegalStateException e) {
            throw new BusinessException("会话数据异常");
        }
    }

    /** 执行 getSession 对应的业务处理。 */
    public UserSessionVO getSession(String sessionId) {
        try {
            return sessionRedisReader.loadSession(sessionId);
        } catch (IllegalStateException e) {
            throw new BusinessException("会话数据异常");
        }
    }

    /** 使用户的所有会话失效（用于封禁、注销、改密码等场景） */
    public void invalidateUserSession(Long userId) {
        String activeSessionId = redisUtils.get(RedisConstants.ACTIVE_SESSION_PREFIX + userId);
        if (activeSessionId != null && !activeSessionId.isBlank()) {
            invalidateSessionWithLock(activeSessionId, userId);
        }
    }

    /** 使指定会话失效 */
    public void invalidateSession(String sessionId, Long userId) {
        if (sessionId != null && !sessionId.isBlank()) {
            if (userId == null) {
                redisUtils.invalidateSession(
                        RedisConstants.SESSION_PREFIX + sessionId,
                        RedisConstants.SESSION_ACTIVE_PREFIX + sessionId);
            } else {
                redisUtils.invalidateSession(
                        RedisConstants.SESSION_PREFIX + sessionId,
                        RedisConstants.SESSION_ACTIVE_PREFIX + sessionId,
                        RedisConstants.ACTIVE_SESSION_PREFIX + userId,
                        sessionId);
            }
        }
    }

    /** 作废与 refresh 共用同一把会话锁，避免作废后被 refresh 的 saveSession 复活。 */
    public void invalidateSessionWithLock(String sessionId, Long userId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        String lockKey = RedisConstants.REFRESH_LOCK_PREFIX + sessionId;
        String lockToken = UUID.randomUUID().toString();
        if (Boolean.FALSE.equals(redisUtils.setIfAbsent(lockKey, lockToken, UserConstants.SESSION_LOCK_SECONDS))) {
            throw new BusinessException(ApiErrorCodes.TOO_MANY_REQUESTS, "登录态处理中，请稍后重试");
        }
        try {
            // 作废与 refresh 共用锁，避免 refresh 在作废之后重新保存会话。
            invalidateSession(sessionId, userId);
        } finally {
            redisUtils.unlock(lockKey, lockToken);
        }
    }

    /** 执行 hashToken 对应的业务处理。 */
    public String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance(UserSessionConstants.TOKEN_DIGEST_ALGORITHM);
            byte[] bytes = digest.digest((token + Constants.REFRESH_JWT_SECRET).getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte current : bytes) {
                builder.append(String.format("%02x", current));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new BusinessException("令牌摘要失败");
        }
    }

    /** 执行 isSessionOnline 对应的业务处理。 */
    public boolean isSessionOnline(UserSessionVO session) {
        return session != null && SessionStatus.ONLINE == session.getStatus();
    }

    /** 执行 writeSession 对应的业务处理。 */
    private String writeSession(UserSessionVO session) {
        try {
            return objectMapper.writeValueAsString(session);
        } catch (JsonProcessingException e) {
            throw new BusinessException("会话数据异常");
        }
    }
}
