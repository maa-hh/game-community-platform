package com.game.community.user.common.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.game.community.common.constant.ApiErrorCodes;
import com.game.community.common.exception.BusinessException;
import com.game.community.model.entity.user.User;
import com.game.community.model.entity.user.UserAuth;
import com.game.community.model.entity.user.UserOperationLog;
import com.game.community.model.enums.user.OperationType;
import com.game.community.model.enums.user.UserStrings;
import com.game.community.user.mapper.UserAuthMapper;
import com.game.community.user.mapper.UserMapper;
import com.game.community.user.mapper.UserOperationLogMapper;
import com.game.community.utils.ThreadLocal.UserThreadLocal;
import com.game.community.utils.email.PasswordValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 用户服务内共享能力：当前用户、认证查询、操作日志
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserSupport {

    private final UserMapper userMapper;
    private final UserAuthMapper userAuthMapper;
    private final UserOperationLogMapper userOperationLogMapper;

    public Long requireUserId() {
        Long userId = UserThreadLocal.getUserId();
        if (userId == null) {
            throw new BusinessException(ApiErrorCodes.UNAUTHORIZED, "请先登录");
        }
        return userId;
    }

    public UserAuth getUserAuth(Long userId) {
        if (userId == null) {
            return null;
        }
        return userAuthMapper.selectOne(new LambdaQueryWrapper<UserAuth>()
                .eq(UserAuth::getUserId, userId));
    }

    public boolean existsByEmail(String email) {
        return userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getEmail, email)) > 0;
    }

    public static void assertValidPassword(String password) {
        try {
            PasswordValidator.assertValid(password);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ApiErrorCodes.BAD_REQUEST, e.getMessage());
        }
    }

    public void logOperation(Long userId, OperationType operation, String detail, String ip) {
        try {
            UserOperationLog logEntry = new UserOperationLog();
            logEntry.setUserId(userId);
            logEntry.setOperatorId(userId);
            logEntry.setOperation(operation);
            logEntry.setDetail(detail == null ? UserStrings.EMPTY : detail);
            logEntry.setIp(ip == null ? UserStrings.EMPTY : ip);
            logEntry.setCreateTime(LocalDateTime.now());
            userOperationLogMapper.insert(logEntry);
        } catch (Exception e) {
            log.warn("记录操作日志失败: userId={}, operation={}", userId, operation, e);
        }
    }
}
