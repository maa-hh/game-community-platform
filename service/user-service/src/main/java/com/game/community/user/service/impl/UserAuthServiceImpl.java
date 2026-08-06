package com.game.community.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.game.community.common.constant.ApiErrorCodes;
import com.game.community.common.constant.AuthErrorCodes;
import com.game.community.common.constant.Constants;
import com.game.community.common.constant.user.RedisConstants;
import com.game.community.common.constant.user.UserConstants;
import com.game.community.common.exception.BusinessException;
import com.game.community.model.base.Result;
import com.game.community.model.dto.user.AccountLoginDTO;
import com.game.community.model.dto.user.RegisterDTO;
import com.game.community.model.dto.user.ResetPasswordDTO;
import com.game.community.model.dto.user.SendCodeDTO;
import com.game.community.model.entity.user.AccountIdPool;
import com.game.community.model.entity.user.User;
import com.game.community.model.entity.user.UserAccount;
import com.game.community.model.entity.user.UserAuth;
import com.game.community.model.enums.user.AccountType;
import com.game.community.model.enums.user.CodeBizType;
import com.game.community.model.enums.user.OperationType;
import com.game.community.model.enums.user.RegisterSource;
import com.game.community.model.enums.user.UserAccountStatus;
import com.game.community.model.enums.user.UserStrings;
import com.game.community.model.vo.user.LoginVO;
import com.game.community.model.vo.user.RegisterVO;
import com.game.community.model.vo.user.SendCodeVO;
import com.game.community.model.vo.user.TokenRefreshVO;
import com.game.community.model.vo.user.UserSessionVO;
import com.game.community.user.mapper.AccountIdPoolMapper;
import com.game.community.user.audit.UserAuditHelper;
import com.game.community.user.mapper.UserAccountMapper;
import com.game.community.user.mapper.UserAuthMapper;
import com.game.community.user.mapper.UserMapper;
import com.game.community.user.service.UserAccountService;
import com.game.community.user.service.UserAuthService;
import com.game.community.user.common.session.UserSessionHelper;
import com.game.community.user.common.support.UserSupport;
import com.game.community.user.common.verification.VerificationCodeHelper;
import com.game.community.utils.EncryptUtils;
import com.game.community.utils.JwtUtils;
import com.game.community.utils.RedisUtils;
import com.game.community.utils.config.EmailProperties;
import com.game.community.utils.email.EmailValidator;
import com.game.community.utils.web.ClientInfo;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.UUID;

/**
 * 用户认证业务实现。
 * <p>
 * 不接触 HttpServlet/Cookie；会话与令牌委托 {@link UserSessionHelper}（Redis + JWT）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserAuthServiceImpl implements UserAuthService {

    private static final DateTimeFormatter LOCK_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final UserMapper userMapper;
    private final UserAccountMapper userAccountMapper;
    private final UserAuthMapper userAuthMapper;
    private final AccountIdPoolMapper accountIdPoolMapper;
    private final UserSessionHelper sessionHelper;
    private final VerificationCodeHelper verificationCodeHelper;
    private final RedisUtils redisUtils;
    private final EmailProperties emailProperties;
    private final UserAccountService userAccountService;
    private final UserSupport userSupport;
    private final UserAuditHelper auditHelper;

    @Override
    public Result<SendCodeVO> sendCode(SendCodeDTO dto) {
        String normalized = assertDeliverableEmail(dto.getEmail());
        CodeBizType type = VerificationCodeHelper.normalizeBizType(dto.getBizType());

        if (type == CodeBizType.REGISTER) {
            if (userSupport.existsByEmail(normalized)) {
                throw new BusinessException(ApiErrorCodes.CONFLICT, "该邮箱已被注册");
            }
        } else if (type == CodeBizType.RESET_PASSWORD) {
            if (!userSupport.existsByEmail(normalized)) {
                throw new BusinessException(ApiErrorCodes.NOT_FOUND, "该邮箱未注册");
            }
        } else if (type == CodeBizType.CANCEL_ACCOUNT) {
            throw new BusinessException(ApiErrorCodes.BAD_REQUEST, "请登录后在账号安全中申请注销验证码");
        }

        int expireIn = verificationCodeHelper.sendEmailCode(normalized, type);
        SendCodeVO vo = new SendCodeVO();
        vo.setExpireIn(expireIn);
        return Result.success("验证码已发送", vo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<RegisterVO> register(RegisterDTO dto) {
        String email = assertDeliverableEmail(dto.getEmail());
        UserSupport.assertValidPassword(dto.getPassword());
        verificationCodeHelper.verify(email, CodeBizType.REGISTER, dto.getCode());

        String lockKey = RedisConstants.REGISTER_LOCK_PREFIX + "email:" + email;
        if (Boolean.FALSE.equals(redisUtils.setIfAbsent(lockKey, "1", 10))) {
            throw new BusinessException(ApiErrorCodes.TOO_MANY_REQUESTS, "操作过于频繁，请稍后重试");
        }

        if (userSupport.existsByEmail(email)) {
            throw new BusinessException(ApiErrorCodes.CONFLICT, "该邮箱已被注册");
        }

        User user = new User();
        user.setUsername(extractNickname(email));
        user.setEmail(email);
        user.setSteamAccount("");
        user.setVersion(0);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        user.setDeleted(0);
        Long accountId = reserveAccountId();
        user.setAccountId(accountId);
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ApiErrorCodes.CONFLICT, "该邮箱已被注册");
        }
        bindAccountIdToUser(accountId, user.getId());
        auditHelper.initProfileAudit(user.getId());
        createUserAccount(user.getId(), RegisterSource.EMAIL);
        createUserAuth(user.getId(), dto.getPassword());

        RegisterVO vo = new RegisterVO();
        vo.setEmail(email);
        return Result.success("注册成功", vo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> resetPassword(ResetPasswordDTO dto) {
        String email = assertDeliverableEmail(dto.getEmail());
        UserSupport.assertValidPassword(dto.getPassword());
        verificationCodeHelper.verify(email, CodeBizType.RESET_PASSWORD, dto.getCode());

        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getEmail, email));
        if (user == null) {
            throw new BusinessException(ApiErrorCodes.NOT_FOUND, "该邮箱未注册");
        }

        UserAuth userAuth = userSupport.getUserAuth(user.getId());
        if (userAuth == null) {
            throw new BusinessException(ApiErrorCodes.INTERNAL_ERROR, "账号数据异常");
        }

        userAuthMapper.update(null, new LambdaUpdateWrapper<UserAuth>()
                .eq(UserAuth::getId, userAuth.getId())
                .set(UserAuth::getPassword, EncryptUtils.bcryptEncode(dto.getPassword()))
                .set(UserAuth::getSalt, "")
                .set(UserAuth::getFailCount, 0)
                .set(UserAuth::getLockUntil, null)
                .set(UserAuth::getLastPasswordChange, LocalDateTime.now())
                .set(UserAuth::getUpdateTime, LocalDateTime.now()));

        sessionHelper.invalidateUserSession(user.getId());
        userSupport.logOperation(user.getId(), OperationType.RESET_PASSWORD, null, null);
        return Result.success("密码重置成功");
    }

    @Override
    public Result<LoginVO> login(AccountLoginDTO dto, ClientInfo clientInfo) {
        String email = assertDeliverableEmail(dto.getEmail());
        UserSupport.assertValidPassword(dto.getPassword());

        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getEmail, email));
        if (user == null) {
            throw new BusinessException(ApiErrorCodes.NOT_FOUND, "该邮箱未注册");
        }

        // refreshStatus：按 DB 回写封禁/注销状态；assertLoginAllowed：非 NORMAL 则拒登
        UserAccount account = userAccountService.refreshStatus(user.getId());
        userAccountService.assertLoginAllowed(account);
        if (account.getStatus() == UserAccountStatus.CANCELLING) {
            userAccountService.revokeCancel(user.getId());
            account = userAccountService.refreshStatus(user.getId());
            log.info("冷静期内登录，已撤销注销: userId={}", user.getId());
        }

        UserAuth userAuth = userSupport.getUserAuth(user.getId());
        if (userAuth == null) {
            throw new BusinessException(ApiErrorCodes.UNAUTHORIZED, "密码错误");
        }

        assertLoginNotLocked(userAuth);

        if (!EncryptUtils.bcryptCheck(dto.getPassword(), userAuth.getPassword())) {
            handleLoginFailure(user.getId(), userAuth);
            throw new BusinessException(ApiErrorCodes.UNAUTHORIZED, "密码错误");
        }

        // buildLoginVO：写 Redis 会话，生成 access+refresh JWT，组装 LoginVO
        LoginVO loginVO = completeLogin(user, account, userAuth, clientInfo.ip());
        return Result.success("登录成功", loginVO);
    }

    @Override
    public Result<TokenRefreshVO> refreshToken(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            throw refreshExpired("refreshToken Cookie 不存在或已失效");
        }

        Claims claims;
        try {
            claims = JwtUtils.parseToken(Constants.REFRESH_JWT_SECRET, refreshToken);
        } catch (Exception e) {
            throw refreshExpired("refreshToken 无效");
        }

        if (!"refresh".equals(claims.get("tokenType", String.class))) {
            throw refreshExpired("refreshToken 无效");
        }
        String sessionId = claims.get("sessionId", String.class);
        if (!StringUtils.hasText(sessionId)) {
            throw refreshExpired("refreshToken 无效");
        }

        String refreshLockKey = RedisConstants.REFRESH_LOCK_PREFIX + sessionId;
        if (Boolean.FALSE.equals(redisUtils.setIfAbsent(refreshLockKey, "1", 5))) {
            throw new BusinessException(ApiErrorCodes.TOO_MANY_REQUESTS, "登录态刷新中，请稍后重试");
        }

        try {
            UserSessionVO session = sessionHelper.loadSessionForAuth(sessionId);
            if (!sessionHelper.isSessionOnline(session)) {
                throw refreshExpired("refreshToken 已过期，请重新登录");
            }
            Long userId = session.getUserId();
            if (userId == null) {
                throw refreshExpired("refreshToken 无效");
            }

            // hashToken：SHA-256(refresh+密钥)，与 session 内存的哈希比对，防旧 refresh 重放
            if (!sessionHelper.hashToken(refreshToken).equals(session.getRefreshTokenHash())) {
                throw refreshExpired("refreshToken 已失效");
            }

            User user = userMapper.selectById(userId);
            if (user == null) {
                sessionHelper.invalidateSession(sessionId, userId);
                throw refreshExpired("用户不存在，请重新登录");
            }
            UserAccount account;
            try {
                account = userAccountService.refreshStatus(userId);
                userAccountService.assertLoginAllowed(account);
            } catch (BusinessException e) {
                sessionHelper.invalidateSession(sessionId, userId);
                throw refreshExpired("账号状态异常，请重新登录");
            }

            String newRefreshToken = sessionHelper.generateRefreshToken(sessionId);
            session.setAccountId(user.getAccountId());
            session.setType(account.getType());
            session.setSteamAccount(UserStrings.orEmpty(user.getSteamAccount()));
            session.setAccountStatus(account.getStatus());
            session.setRefreshTokenHash(sessionHelper.hashToken(newRefreshToken));
            session.setLastRefreshTime(LocalDateTime.now());
            sessionHelper.saveSession(sessionId, userId, session);

            TokenRefreshVO vo = new TokenRefreshVO();
            vo.setAccessToken(sessionHelper.generateAccessToken(user, account, sessionId));
            vo.setAccessExpiresIn(Constants.ACCESS_TOKEN_EXPIRE_TIME);
            vo.setRefreshToken(newRefreshToken);
            return Result.success("令牌刷新成功", vo);
        } finally {
            redisUtils.del(refreshLockKey);
        }
    }

    @Override
    public Result<Void> logout(Long userId, String sessionId, String refreshToken) {
        // access 过期时 ThreadLocal 无 userId，从 refresh JWT 取 sessionId 再查 Redis 会话
        if (userId == null && StringUtils.hasText(refreshToken)) {
            try {
                Claims claims = JwtUtils.parseToken(Constants.REFRESH_JWT_SECRET, refreshToken);
                String refreshSessionId = claims.get("sessionId", String.class);
                if (StringUtils.hasText(refreshSessionId)) {
                    sessionId = refreshSessionId;
                    UserSessionVO session = sessionHelper.getSession(refreshSessionId);
                    if (session != null) {
                        userId = session.getUserId();
                    }
                }
            } catch (Exception e) {
                // 解析失败仍返回成功，Controller 负责清 Cookie
                log.debug("登出：refresh 无法解析，跳过 Redis 作废: {}", e.getMessage());
            }
        }

        if (userId != null) {
            String currentSessionId = StringUtils.hasText(sessionId)
                    ? sessionId
                    : redisUtils.get(RedisConstants.ACTIVE_SESSION_PREFIX + userId);
            sessionHelper.invalidateSession(currentSessionId, userId);
            userSupport.logOperation(userId, OperationType.LOGOUT, null, null);
        }
        return Result.success("退出登录成功");
    }

    /** 登录成功收尾：清旧会话 → 记登录日志 → 生成双 token（LoginVO.refreshToken 供 Controller 写 Cookie） */
    private LoginVO completeLogin(User user, UserAccount account, UserAuth userAuth, String loginIp) {
        String loginLockKey = RedisConstants.LOGIN_LOCK_PREFIX + user.getId();
        if (Boolean.FALSE.equals(redisUtils.setIfAbsent(loginLockKey, "1", 5))) {
            throw new BusinessException(ApiErrorCodes.TOO_MANY_REQUESTS, "登录处理中，请稍后重试");
        }

        try {
            userAuthMapper.update(null, new LambdaUpdateWrapper<UserAuth>()
                    .eq(UserAuth::getId, userAuth.getId())
                    .set(UserAuth::getFailCount, 0)
                    .set(UserAuth::getLockUntil, null)
                    .set(UserAuth::getUpdateTime, LocalDateTime.now()));

            userAccountMapper.update(null, new LambdaUpdateWrapper<UserAccount>()
                    .eq(UserAccount::getId, account.getId())
                    .set(UserAccount::getLastLoginTime, LocalDateTime.now())
                    .set(UserAccount::getLastLoginIp, loginIp)
                    .set(UserAccount::getUpdateTime, LocalDateTime.now()));

            sessionHelper.invalidateUserSession(user.getId());
            userSupport.logOperation(user.getId(), OperationType.LOGIN, null, loginIp);
            return sessionHelper.buildLoginVO(user, account, UUID.randomUUID().toString());
        } finally {
            redisUtils.del(loginLockKey);
        }
    }

    private void assertLoginNotLocked(UserAuth userAuth) {
        if (userAuth.getLockUntil() == null) {
            return;
        }
        if (userAuth.getLockUntil().isAfter(LocalDateTime.now())) {
            throw new BusinessException(ApiErrorCodes.FORBIDDEN, "密码错误次数过多，账号已锁定至 "
                    + userAuth.getLockUntil().format(LOCK_TIME_FORMAT) + "，请稍后再试");
        }
        userAuthMapper.update(null, new LambdaUpdateWrapper<UserAuth>()
                .eq(UserAuth::getId, userAuth.getId())
                .set(UserAuth::getFailCount, 0)
                .set(UserAuth::getLockUntil, null)
                .set(UserAuth::getUpdateTime, LocalDateTime.now()));
        userAuth.setFailCount(0);
        userAuth.setLockUntil(null);
    }

    private String assertDeliverableEmail(String email) {
        String normalized = EmailValidator.normalize(email);
        try {
            EmailValidator.assertDeliverable(normalized, emailProperties.getValidation().isMxCheckEnabled());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ApiErrorCodes.BAD_REQUEST, e.getMessage());
        }
        return normalized;
    }

    private Long reserveAccountId() {
        int maxRetries = 3;
        for (int retry = 0; retry < maxRetries; retry++) {
            AccountIdPool available = accountIdPoolMapper.selectOne(
                    new LambdaQueryWrapper<AccountIdPool>()
                            .eq(AccountIdPool::getStatus, 0)
                            .orderByAsc(AccountIdPool::getDigitCount)
                            .orderByAsc(AccountIdPool::getAccountId)
                            .last("LIMIT 1"));

            if (available == null) {
                expandPool();
                continue;
            }

            int rows = accountIdPoolMapper.casReserve(available.getAccountId());
            if (rows > 0) {
                log.info("号池预占成功: accountId={}", available.getAccountId());
                return available.getAccountId();
            }
            log.info("号池CAS预占失败，重试: retry={}", retry + 1);
        }
        throw new BusinessException(ApiErrorCodes.INTERNAL_ERROR, "账号ID分配失败，请稍后重试");
    }

    private void bindAccountIdToUser(Long accountId, Long userId) {
        int rows = accountIdPoolMapper.bindUserId(accountId, userId);
        if (rows <= 0) {
            throw new BusinessException(ApiErrorCodes.INTERNAL_ERROR, "账号ID绑定失败");
        }
        log.info("号池绑定成功: userId={}, accountId={}", userId, accountId);
    }

    /**
     * 号池耗尽时分批扩容：从当前最大 accountId+1 起插入一批可用 ID。
     * 初始档 5 位（10000-99999），耗尽后升到 6 位……不超过 {@link UserConstants#ACCOUNT_ID_MAX}。
     */
    private void expandPool() {
        AccountIdPool maxRow = accountIdPoolMapper.selectOne(
                new LambdaQueryWrapper<AccountIdPool>()
                        .orderByDesc(AccountIdPool::getAccountId)
                        .last("LIMIT 1"));

        long startId;
        int digitCount;
        if (maxRow == null) {
            startId = UserConstants.ACCOUNT_ID_MIN;
            digitCount = UserConstants.ACCOUNT_ID_INITIAL_DIGITS;
        } else {
            startId = maxRow.getAccountId() + 1;
            if (startId > UserConstants.ACCOUNT_ID_MAX) {
                throw new BusinessException(ApiErrorCodes.INTERNAL_ERROR, "账号ID号池已耗尽");
            }
            digitCount = String.valueOf(startId).length();
        }

        long digitEnd = Math.min(pow10(digitCount) - 1, UserConstants.ACCOUNT_ID_MAX);
        long endId = Math.min(startId + UserConstants.ACCOUNT_ID_EXPAND_BATCH - 1, digitEnd);
        if (endId < startId) {
            throw new BusinessException(ApiErrorCodes.INTERNAL_ERROR, "账号ID号池扩容失败");
        }

        ArrayList<Long> ids = new ArrayList<>((int) (endId - startId + 1));
        for (long id = startId; id <= endId; id++) {
            ids.add(id);
        }
        int inserted = accountIdPoolMapper.insertBatch(ids, digitCount);
        log.info("号池扩容完成: digits={}, range=[{}, {}], inserted={}", digitCount, startId, endId, inserted);
    }

    private static long pow10(int digits) {
        long v = 1L;
        for (int i = 0; i < digits; i++) {
            v *= 10L;
        }
        return v;
    }

    private void createUserAccount(Long userId, RegisterSource registerSource) {
        UserAccount account = new UserAccount();
        account.setUserId(userId);
        account.setStatus(UserAccountStatus.NORMAL);
        account.setType(AccountType.NORMAL);
        account.setRegisterSource(registerSource);
        account.setBanReason(UserStrings.EMPTY);
        account.setLastLoginIp(UserStrings.EMPTY);
        account.setVersion(0);
        account.setCreateTime(LocalDateTime.now());
        account.setUpdateTime(LocalDateTime.now());
        account.setDeleted(0);
        userAccountMapper.insert(account);
    }

    private void createUserAuth(Long userId, String password) {
        LocalDateTime now = LocalDateTime.now();
        UserAuth userAuth = new UserAuth();
        userAuth.setUserId(userId);
        userAuth.setPassword(EncryptUtils.bcryptEncode(password));
        userAuth.setSalt("");
        userAuth.setFailCount(0);
        userAuth.setVersion(0);
        userAuth.setCreateTime(now);
        userAuth.setUpdateTime(now);
        userAuth.setLastPasswordChange(now);
        userAuth.setDeleted(0);
        userAuthMapper.insert(userAuth);
    }

    private void handleLoginFailure(Long userId, UserAuth userAuth) {
        int newFailCount = (userAuth.getFailCount() == null ? 0 : userAuth.getFailCount()) + 1;
        if (newFailCount >= UserConstants.LOGIN_FAIL_LOCK_THRESHOLD) {
            LocalDateTime lockUntil = LocalDateTime.now()
                    .plusMinutes(UserConstants.LOGIN_LOCK_DURATION_MINUTES);
            userAuthMapper.update(null, new LambdaUpdateWrapper<UserAuth>()
                    .eq(UserAuth::getId, userAuth.getId())
                    .set(UserAuth::getFailCount, newFailCount)
                    .set(UserAuth::getLockUntil, lockUntil)
                    .set(UserAuth::getUpdateTime, LocalDateTime.now()));
            log.warn("登录失败锁定: userId={}, failCount={}, lockUntil={}", userId, newFailCount, lockUntil);
        } else {
            userAuthMapper.update(null, new LambdaUpdateWrapper<UserAuth>()
                    .eq(UserAuth::getId, userAuth.getId())
                    .set(UserAuth::getFailCount, newFailCount)
                    .set(UserAuth::getUpdateTime, LocalDateTime.now()));
        }
    }

    private static String extractNickname(String email) {
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : "新玩家";
    }

    private static BusinessException refreshExpired(String message) {
        return new BusinessException(AuthErrorCodes.REFRESH_EXPIRED, message);
    }
}
