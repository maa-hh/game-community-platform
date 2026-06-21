package com.game.community.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.community.common.constant.Constants;
import com.game.community.common.constant.user.RedisConstants;
import com.game.community.common.constant.user.UserConstants;
import com.game.community.common.exception.BusinessException;
import com.game.community.model.base.PageResult;
import com.game.community.model.dto.user.AccountLoginDTO;
import com.game.community.model.dto.user.ChangePasswordDTO;
import com.game.community.model.dto.user.RegisterDTO;
import com.game.community.model.dto.user.UpdateUserInfoDTO;
import com.game.community.model.entity.user.User;
import com.game.community.model.entity.user.UserAuditTask;
import com.game.community.model.entity.user.UserAuth;
import com.game.community.model.payload.user.AvatarAuditPayload;
import com.game.community.model.payload.user.ProfileAuditPayload;
import com.game.community.model.vo.user.LoginVO;
import com.game.community.model.vo.user.TokenRefreshVO;
import com.game.community.model.vo.user.UserSessionVO;
import com.game.community.model.vo.user.UserSimpleVO;
import com.game.community.model.vo.user.UserVO;
import com.game.community.user.mapper.UserAuditTaskMapper;
import com.game.community.user.mapper.UserAuthMapper;
import com.game.community.user.mapper.UserMapper;
import com.game.community.user.service.AvatarAuditTaskService;
import com.game.community.user.service.IUserService;
import com.game.community.user.service.UserAuditService;
import com.game.community.user.service.UserProfileAuditTaskService;
import com.game.community.utils.EncryptUtils;
import com.game.community.utils.JwtUtils;
import com.game.community.utils.MinIOUtils;
import com.game.community.utils.RedisUtils;
import com.game.community.utils.ThreadLocal.UserThreadLocal;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 用户服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    private static final String TOKEN_TYPE_ACCESS = "access";

    private static final String TOKEN_TYPE_REFRESH = "refresh";

    private static final String SESSION_STATUS_ONLINE = "ONLINE";

    private final RedisUtils redisUtils;

    private final UserAuditService userAuditService;

    private final MinIOUtils minIOUtils;

    private final UserAuditTaskMapper userAuditTaskMapper;

    private final UserAuthMapper userAuthMapper;

    private final UserMapper userMapper;

    private final AvatarAuditTaskService avatarAuditTaskService;

    private final UserProfileAuditTaskService userProfileAuditTaskService;

    private final ObjectMapper objectMapper;

    @Override
    public String sendCode(String phone) {
        String code = String.format("%06d", new Random().nextInt(1000000));
        redisUtils.setEx(RedisConstants.CODE_PREFIX + phone, code, UserConstants.CODE_EXPIRE);
        return code;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long registerByPhone(RegisterDTO dto) {
        String lockKey = RedisConstants.REGISTER_LOCK_PREFIX + dto.getPhone();
        Boolean locked = redisUtils.setIfAbsent(lockKey, "1", 10);
        if (Boolean.FALSE.equals(locked)) {
            throw new BusinessException("操作过于频繁，请稍后重试");
        }

        String code = redisUtils.get(RedisConstants.CODE_PREFIX + dto.getPhone());
        if (code == null) {
            throw new BusinessException("验证码已过期");
        }
        if (!code.equals(dto.getCode())) {
            throw new BusinessException("验证码错误");
        }

        long count = count(new LambdaQueryWrapper<User>()
                .eq(User::getPhone, dto.getPhone()));
        if (count > 0) {
            throw new BusinessException("手机号已被注册");
        }

        if (!userAuditService.auditUserInfo(dto.getUsername(), null)) {
            throw new BusinessException("用户信息审核未通过");
        }

        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPhone(dto.getPhone());
        user.setStatus(UserConstants.UserStatus.NORMAL);
        user.setType(UserConstants.UserType.NORMAL);
        user.setGameAccount(StringUtils.hasText(dto.getGameAccount()) ? dto.getGameAccount() : "");
        user.setAuditStatus(UserConstants.AuditStatus.NONE);
        user.setVersion(0);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        user.setDeleted(0);
        try {
            save(user);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("手机号已被注册");
        }
        fillAccountId(user);

        String salt = EncryptUtils.generateSalt();
        UserAuth userAuth = new UserAuth();
        userAuth.setUserId(user.getId());
        userAuth.setPassword(EncryptUtils.md5WithSalt(dto.getPassword(), salt));
        userAuth.setSalt(salt);
        userAuth.setVersion(0);
        userAuth.setCreateTime(LocalDateTime.now());
        userAuth.setUpdateTime(LocalDateTime.now());
        userAuth.setDeleted(0);
        userAuthMapper.insert(userAuth);

        redisUtils.del(RedisConstants.CODE_PREFIX + dto.getPhone());
        return user.getAccountId();
    }

    @Override
    public LoginVO loginByAccount(AccountLoginDTO dto) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .eq(User::getAccountId, dto.getAccountId());
        if (dto.getType() != null) {
            wrapper.eq(User::getType, dto.getType());
        }
        User user = getOne(wrapper);

        if (user == null) {
            throw new BusinessException("账号不存在");
        }
        UserAuth userAuth = getUserAuth(user.getId());
        if (userAuth == null || !EncryptUtils.md5Check(dto.getPassword(), userAuth.getPassword(), userAuth.getSalt())) {
            throw new BusinessException("账号或密码错误");
        }
        if (UserConstants.UserStatus.DISABLED == user.getStatus()) {
            throw new BusinessException("账号已被禁用");
        }
        String loginLockKey = RedisConstants.LOGIN_LOCK_PREFIX + user.getId();
        Boolean locked = redisUtils.setIfAbsent(loginLockKey, "1", 5);
        if (Boolean.FALSE.equals(locked)) {
            throw new BusinessException("登录处理中，请稍后重试");
        }
        try {
            update(new LambdaUpdateWrapper<User>()
                    .eq(User::getId, user.getId())
                    .set(User::getLastLoginTime, LocalDateTime.now()));
            invalidateUserSession(user.getId());
            return buildLoginVO(user, UUID.randomUUID().toString());
        } finally {
            redisUtils.del(loginLockKey);
        }
    }

    @Override
    public TokenRefreshVO refreshToken(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            throw new BusinessException("登录已过期，请重新登录");
        }
        Claims claims;
        try {
            claims = JwtUtils.parseToken(Constants.REFRESH_JWT_SECRET, refreshToken);
        } catch (Exception e) {
            throw new BusinessException("登录已过期，请重新登录");
        }

        if (!TOKEN_TYPE_REFRESH.equals(claims.get("tokenType", String.class))) {
            throw new BusinessException("refresh token无效");
        }
        Long userId = getLongClaim(claims, "userId");
        String sessionId = claims.get("sessionId", String.class);
        if (userId == null || !StringUtils.hasText(sessionId)) {
            throw new BusinessException("refresh token无效");
        }

        String refreshLockKey = RedisConstants.REFRESH_LOCK_PREFIX + sessionId;
        Boolean locked = redisUtils.setIfAbsent(refreshLockKey, "1", 5);
        if (Boolean.FALSE.equals(locked)) {
            throw new BusinessException("登录态刷新中，请稍后重试");
        }
        try {
            UserSessionVO session = getSession(sessionId);
            if (session == null || !SESSION_STATUS_ONLINE.equals(session.getStatus())) {
                throw new BusinessException("登录已过期，请重新登录");
            }
            if (!userId.equals(session.getUserId())) {
                throw new BusinessException("refresh token无效");
            }
            String activeSessionId = redisUtils.get(RedisConstants.ACTIVE_SESSION_PREFIX + userId);
            if (!sessionId.equals(activeSessionId)) {
                invalidateSession(sessionId, userId);
                throw new BusinessException("refresh token已失效");
            }
            if (!hashToken(refreshToken).equals(session.getRefreshTokenHash())) {
                invalidateSession(sessionId, userId);
                throw new BusinessException("refresh token已失效");
            }

            User user = getById(userId);
            if (user == null || UserConstants.UserStatus.DISABLED == user.getStatus()) {
                invalidateSession(sessionId, userId);
                throw new BusinessException("用户不存在");
            }

            String newRefreshToken = generateRefreshToken(userId, sessionId);
            session.setType(user.getType());
            session.setGameAccount(user.getGameAccount() == null ? "" : user.getGameAccount());
            session.setRefreshTokenHash(hashToken(newRefreshToken));
            session.setStatus(SESSION_STATUS_ONLINE);
            session.setLastRefreshTime(LocalDateTime.now());
            saveSession(sessionId, userId, session);

            TokenRefreshVO vo = new TokenRefreshVO();
            vo.setAccessToken(generateAccessToken(user, sessionId));
            vo.setAccessTokenExpireIn(Constants.ACCESS_TOKEN_EXPIRE_TIME);
            vo.setRefreshToken(newRefreshToken);
            return vo;
        } finally {
            redisUtils.del(refreshLockKey);
        }
    }

    @Override
    public void logout(Long userId, String sessionId) {
        String currentSessionId = StringUtils.hasText(sessionId)
                ? sessionId
                : redisUtils.get(RedisConstants.ACTIVE_SESSION_PREFIX + userId);
        invalidateSession(currentSessionId, userId);
    }

    @Override
    public UserVO getCurrentUser(Long userId) {
        User user = getById(userId);
        if (user == null || UserConstants.UserStatus.DISABLED == user.getStatus()) {
            throw new BusinessException("用户不存在");
        }
        UserVO vo = convertToVO(user);
        vo.setPendingAvatarUrl(resolvePendingAvatarUrl(userId));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(Long userId, ChangePasswordDTO dto) {
        User user = getById(userId);
        if (user == null || UserConstants.UserStatus.DISABLED == user.getStatus()) {
            throw new BusinessException("用户不存在");
        }
        UserAuth userAuth = getUserAuth(userId);
        if (userAuth == null || !EncryptUtils.md5Check(dto.getOldPassword(), userAuth.getPassword(), userAuth.getSalt())) {
            throw new BusinessException("原密码错误");
        }
        if (dto.getOldPassword().equals(dto.getNewPassword())) {
            throw new BusinessException("新密码不能与原密码相同");
        }

        String newSalt = EncryptUtils.generateSalt();
        boolean updated = userAuthMapper.update(null, new LambdaUpdateWrapper<UserAuth>()
                .eq(UserAuth::getUserId, userId)
                .eq(UserAuth::getVersion, userAuth.getVersion())
                .set(UserAuth::getSalt, newSalt)
                .set(UserAuth::getPassword, EncryptUtils.md5WithSalt(dto.getNewPassword(), newSalt))
                .set(UserAuth::getVersion, userAuth.getVersion() + 1)
                .set(UserAuth::getUpdateTime, LocalDateTime.now())) > 0;
        if (!updated) {
            throw new BusinessException("密码已被更新，请刷新后重试");
        }
        invalidateUserSession(userId);
    }

    @Override
    public UserVO getUserVOById(Long id) {
        User user = getById(id);
        if (user == null || UserConstants.UserStatus.DISABLED == user.getStatus()) {
            throw new BusinessException("用户不存在");
        }
        UserVO vo = convertToVO(user);
        Long currentUserId = UserThreadLocal.getUserId();
        Integer currentUserType = UserThreadLocal.getType();
        boolean canViewPhone = id.equals(currentUserId)
                || (currentUserType != null && currentUserType == UserConstants.UserType.ADMIN);
        if (!canViewPhone) {
            vo.setPhone(null);
        }
        if (id.equals(currentUserId)) {
            vo.setPendingAvatarUrl(resolvePendingAvatarUrl(id));
        }
        return vo;
    }

    @Override
    public UserVO getUserVOByAccountId(Long accountId) {
        User user = getByAccountId(accountId);
        UserVO vo = convertToVO(user);
        Long currentUserId = UserThreadLocal.getUserId();
        Integer currentUserType = UserThreadLocal.getType();
        boolean canViewPhone = user.getId().equals(currentUserId)
                || (currentUserType != null && currentUserType == UserConstants.UserType.ADMIN);
        if (!canViewPhone) {
            vo.setPhone(null);
        }
        if (user.getId().equals(currentUserId)) {
            vo.setPendingAvatarUrl(resolvePendingAvatarUrl(user.getId()));
        }
        return vo;
    }

    @Override
    public UserSimpleVO getUserSimpleById(Long id) {
        User user = getById(id);
        if (user == null || UserConstants.UserStatus.DISABLED == user.getStatus()) {
            throw new BusinessException("用户不存在");
        }
        return convertToSimpleVO(user);
    }

    @Override
    public UserSimpleVO getUserSimpleByAccountId(Long accountId) {
        return convertToSimpleVO(getByAccountId(accountId));
    }

    @Override
    public List<UserVO> getUsersByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return listByIds(ids).stream().map(this::convertToVO).toList();
    }

    @Override
    public PageResult<UserSimpleVO> getUserSimplePageByUsernamePrefix(Integer page, Integer size, String usernamePrefix) {
        int current = page == null || page < 1 ? 1 : page;
        int pageSize = size == null || size < 1 ? 10 : size;
        Page<User> userPage = page(new Page<>(current, pageSize), new LambdaQueryWrapper<User>()
                .like(StringUtils.hasText(usernamePrefix), User::getUsername, usernamePrefix)
                .eq(User::getStatus, UserConstants.UserStatus.NORMAL)
                .orderByDesc(User::getCreateTime));
        List<UserSimpleVO> records = userPage.getRecords().stream().map(this::convertToSimpleVO).toList();
        return PageResult.of(records, (long) current, (long) pageSize, userPage.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUserInfo(Long userId, UpdateUserInfoDTO dto) {
        String phone = StringUtils.hasText(dto.getPhone()) ? dto.getPhone() : null;
        User user = getById(userId);
        if (user == null || UserConstants.UserStatus.DISABLED == user.getStatus()) {
            throw new BusinessException("用户不存在");
        }
        if (!Objects.equals(dto.getVersion(), user.getVersion())) {
            throw new BusinessException("资料已更新，请刷新页面后重试");
        }
        if (UserConstants.AuditStatus.AUDITING == defaultAuditStatus(user.getAuditStatus())) {
            throw new BusinessException("用户资料审核中，请稍后再试");
        }

        if (phone != null) {
            long count = count(new LambdaQueryWrapper<User>()
                    .eq(User::getPhone, phone)
                    .ne(User::getId, userId));
            if (count > 0) {
                throw new BusinessException("手机号已被其他用户使用");
            }
        }

        ProfileAuditPayload payload = buildProfileAuditPayload(user, dto, phone);
        if (payload == null) {
            return;
        }

        if (!acquireAuditStatus(user)) {
            User latest = getById(userId);
            if (latest != null && !Objects.equals(dto.getVersion(), latest.getVersion())) {
                throw new BusinessException("资料已更新，请刷新页面后重试");
            }
            throw new BusinessException("用户资料审核中，请稍后再试");
        }

        try {
            payload.setUserVersion(user.getVersion() + 1);
            Long taskId = createAuditTask(userId, UserConstants.AuditTaskType.PROFILE, payload);
            runAfterCommit(() -> userProfileAuditTaskService.auditProfileAsync(taskId));
        } catch (RuntimeException e) {
            clearAuditStatus(userId, user.getVersion() + 1);
            throw e;
        }
    }

    @Override
    public String uploadAvatar(Long userId, MultipartFile avatarFile) {
        User user = getById(userId);
        if (user == null || UserConstants.UserStatus.DISABLED == user.getStatus()) {
            throw new BusinessException("用户不存在");
        }
        if (UserConstants.AuditStatus.AUDITING == defaultAuditStatus(user.getAuditStatus())) {
            throw new BusinessException("用户资料审核中，请稍后再试");
        }
        validateAvatar(avatarFile);

        if (!acquireAuditStatus(user)) {
            throw new BusinessException("用户资料审核中，请稍后再试");
        }

        try {
            String pendingObjectName = minIOUtils.uploadPrivateAvatar(avatarFile, avatarFile.getOriginalFilename());
            String previewUrl = minIOUtils.generatePrivateAvatarUrl(pendingObjectName);
            AvatarAuditPayload payload = new AvatarAuditPayload();
            payload.setUserVersion(user.getVersion() + 1);
            payload.setOldAvatarUrl(user.getAvatar());
            payload.setPendingObjectName(pendingObjectName);
            Long taskId = createAuditTask(userId, UserConstants.AuditTaskType.AVATAR, payload);
            runAfterCommit(() -> avatarAuditTaskService.auditAvatarAsync(taskId));
            return previewUrl;
        } catch (RuntimeException e) {
            clearAuditStatus(userId, user.getVersion() + 1);
            throw e;
        }
    }

    @Override
    public void cancelAccount(Long userId) {
        switchUserStatus(userId, UserConstants.UserStatus.DISABLED);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void switchUserStatus(Long userId, Integer status) {
        User user = getById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (status == null
                || (status != UserConstants.UserStatus.NORMAL && status != UserConstants.UserStatus.DISABLED)) {
            throw new BusinessException("状态值不正确");
        }
        if (status.equals(user.getStatus())) {
            return;
        }
        LambdaUpdateWrapper<User> statusUpdate = new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .eq(User::getVersion, user.getVersion())
                .set(User::getStatus, status)
                .set(User::getVersion, user.getVersion() + 1)
                .set(User::getUpdateTime, LocalDateTime.now());
        if (status == UserConstants.UserStatus.DISABLED) {
            statusUpdate.set(User::getAuditStatus, UserConstants.AuditStatus.NONE);
        }
        int updated = userMapper.update(null, statusUpdate);
        if (updated <= 0) {
            throw new BusinessException("用户状态已变更，请刷新后重试");
        }
        if (status == UserConstants.UserStatus.DISABLED) {
            invalidateUserSession(userId);
        }
    }

    private LoginVO buildLoginVO(User user, String sessionId) {
        String refreshToken = generateRefreshToken(user.getId(), sessionId);
        UserSessionVO session = new UserSessionVO();
        session.setUserId(user.getId());
        session.setType(user.getType());
        session.setGameAccount(user.getGameAccount() == null ? "" : user.getGameAccount());
        session.setRefreshTokenHash(hashToken(refreshToken));
        session.setStatus(SESSION_STATUS_ONLINE);
        session.setLoginTime(LocalDateTime.now());
        session.setLastRefreshTime(LocalDateTime.now());
        saveSession(sessionId, user.getId(), session);

        String accessToken = generateAccessToken(user, sessionId);

        LoginVO vo = new LoginVO();
        vo.setAccessToken(accessToken);
        vo.setAccessTokenExpireIn(Constants.ACCESS_TOKEN_EXPIRE_TIME);
        vo.setRefreshToken(refreshToken);
        vo.setUserId(user.getId());
        vo.setAccountId(user.getAccountId());
        vo.setUsername(user.getUsername());
        vo.setAvatar(user.getAvatar());
        vo.setType(user.getType());
        vo.setGameAccount(user.getGameAccount());
        vo.setAuditStatus(defaultAuditStatus(user.getAuditStatus()));
        return vo;
    }

    private String generateAccessToken(User user, String sessionId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("type", user.getType());
        claims.put("gameAccount", user.getGameAccount() == null ? "" : user.getGameAccount());
        claims.put("sessionId", sessionId);
        claims.put("tokenType", TOKEN_TYPE_ACCESS);
        return JwtUtils.generateToken(Constants.ACCESS_JWT_SECRET, claims, Constants.ACCESS_TOKEN_EXPIRE_TIME * 1000);
    }

    private String generateRefreshToken(Long userId, String sessionId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("sessionId", sessionId);
        claims.put("tokenType", TOKEN_TYPE_REFRESH);
        return JwtUtils.generateToken(Constants.REFRESH_JWT_SECRET, claims, Constants.REFRESH_TOKEN_EXPIRE_TIME * 1000);
    }

    private UserVO convertToVO(User user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setAccountId(user.getAccountId());
        vo.setUsername(user.getUsername());
        vo.setAvatar(user.getAvatar());
        vo.setSignature(user.getSignature());
        vo.setPhone(user.getPhone());
        vo.setStatus(user.getStatus());
        vo.setType(user.getType());
        vo.setGameAccount(user.getGameAccount());
        vo.setAuditStatus(defaultAuditStatus(user.getAuditStatus()));
        vo.setVersion(user.getVersion());
        vo.setFollowCount(0);
        vo.setFansCount(0);
        return vo;
    }

    private ProfileAuditPayload buildProfileAuditPayload(User user, UpdateUserInfoDTO dto, String phone) {
        ProfileAuditPayload payload = new ProfileAuditPayload();
        boolean changed = false;
        if (dto.getUsername() != null && !Objects.equals(dto.getUsername(), user.getUsername())) {
            payload.setUsername(dto.getUsername());
            changed = true;
        }
        if (dto.getSignature() != null && !Objects.equals(dto.getSignature(), user.getSignature())) {
            payload.setSignature(dto.getSignature());
            changed = true;
        }
        if (phone != null && !Objects.equals(phone, user.getPhone())) {
            payload.setPhone(phone);
            changed = true;
        }
        if (dto.getGameAccount() != null && !Objects.equals(dto.getGameAccount(), user.getGameAccount())) {
            payload.setGameAccount(dto.getGameAccount());
            changed = true;
        }
        return changed ? payload : null;
    }

    private UserSimpleVO convertToSimpleVO(User user) {
        UserSimpleVO vo = new UserSimpleVO();
        vo.setId(user.getId());
        vo.setAccountId(user.getAccountId());
        vo.setUsername(user.getUsername());
        vo.setAvatar(user.getAvatar());
        vo.setSignature(user.getSignature());
        vo.setGameAccount(user.getGameAccount());
        return vo;
    }

    private Long createAuditTask(Long userId, String taskType, Object payload) {
        UserAuditTask task = new UserAuditTask();
        task.setUserId(userId);
        task.setTaskType(taskType);
        task.setStatus(UserConstants.AuditTaskStatus.PENDING);
        task.setPayload(writeJson(payload));
        task.setCreateTime(LocalDateTime.now());
        task.setUpdateTime(LocalDateTime.now());
        task.setDeleted(0);
        userAuditTaskMapper.insert(task);
        return task.getId();
    }

    private String resolvePendingAvatarUrl(Long userId) {
        UserAuditTask task = userAuditTaskMapper.selectOne(new LambdaQueryWrapper<UserAuditTask>()
                .eq(UserAuditTask::getUserId, userId)
                .eq(UserAuditTask::getTaskType, UserConstants.AuditTaskType.AVATAR)
                .in(UserAuditTask::getStatus, List.of(
                        UserConstants.AuditTaskStatus.PENDING,
                        UserConstants.AuditTaskStatus.PROCESSING))
                .orderByDesc(UserAuditTask::getCreateTime)
                .last("limit 1"));
        if (task == null || !StringUtils.hasText(task.getPayload())) {
            return null;
        }
        try {
            AvatarAuditPayload payload = objectMapper.readValue(task.getPayload(), AvatarAuditPayload.class);
            return minIOUtils.generatePrivateAvatarUrl(payload.getPendingObjectName());
        } catch (Exception e) {
            log.warn("解析待审核头像失败: userId={}, taskId={}", userId, task.getId(), e);
            return null;
        }
    }

    private boolean acquireAuditStatus(User user) {
        return userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, user.getId())
                .eq(User::getVersion, user.getVersion())
                .eq(User::getAuditStatus, UserConstants.AuditStatus.NONE)
                .set(User::getAuditStatus, UserConstants.AuditStatus.AUDITING)
                .set(User::getVersion, user.getVersion() + 1)
                .set(User::getUpdateTime, LocalDateTime.now())) > 0;
    }

    private void clearAuditStatus(Long userId, Integer version) {
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .eq(User::getVersion, version)
                .set(User::getAuditStatus, UserConstants.AuditStatus.NONE)
                .set(User::getVersion, version + 1)
                .set(User::getUpdateTime, LocalDateTime.now()));
    }

    private int defaultAuditStatus(Integer auditStatus) {
        return auditStatus == null ? UserConstants.AuditStatus.NONE : auditStatus;
    }

    private void saveSession(String sessionId, Long userId, UserSessionVO session) {
        redisUtils.set(RedisConstants.SESSION_PREFIX + sessionId,
                writeSession(session),
                Constants.REFRESH_TOKEN_EXPIRE_TIME,
                TimeUnit.SECONDS);
        redisUtils.set(RedisConstants.ACTIVE_SESSION_PREFIX + userId,
                sessionId,
                Constants.REFRESH_TOKEN_EXPIRE_TIME,
                TimeUnit.SECONDS);
    }

    private UserSessionVO getSession(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return null;
        }
        String sessionText = redisUtils.get(RedisConstants.SESSION_PREFIX + sessionId);
        if (!StringUtils.hasText(sessionText)) {
            return null;
        }
        try {
            return objectMapper.readValue(sessionText, UserSessionVO.class);
        } catch (JsonProcessingException e) {
            throw new BusinessException("会话数据异常");
        }
    }

    private User getByAccountId(Long accountId) {
        if (accountId == null) {
            throw new BusinessException("用户不存在");
        }
        User user = getOne(new LambdaQueryWrapper<User>()
                .eq(User::getAccountId, accountId)
                .eq(User::getStatus, UserConstants.UserStatus.NORMAL));
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return user;
    }

    private UserAuth getUserAuth(Long userId) {
        if (userId == null) {
            return null;
        }
        return userAuthMapper.selectOne(new LambdaQueryWrapper<UserAuth>()
                .eq(UserAuth::getUserId, userId));
    }

    private void fillAccountId(User user) {
        if (user.getId() == null) {
            throw new BusinessException("账号创建失败");
        }
        long accountId = deriveAccountId(user.getId());
        boolean updated = update(new LambdaUpdateWrapper<User>()
                .eq(User::getId, user.getId())
                .isNull(User::getAccountId)
                .set(User::getAccountId, accountId)
                .set(User::getUpdateTime, LocalDateTime.now()));
        if (!updated) {
            throw new BusinessException("账号ID回填失败");
        }
        user.setAccountId(accountId);
    }

    private long deriveAccountId(Long userId) {
        long accountId = userId + 9_999L;
        if (accountId < UserConstants.ACCOUNT_ID_MIN || accountId > UserConstants.ACCOUNT_ID_MAX) {
            throw new BusinessException("账号ID号段已用尽");
        }
        return accountId;
    }

    private String writeSession(UserSessionVO session) {
        try {
            return objectMapper.writeValueAsString(session);
        } catch (JsonProcessingException e) {
            throw new BusinessException("会话数据异常");
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BusinessException("审核任务数据异常");
        }
    }

    private void runAfterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private void invalidateUserSession(Long userId) {
        String activeSessionId = redisUtils.get(RedisConstants.ACTIVE_SESSION_PREFIX + userId);
        invalidateSession(activeSessionId, userId);
    }

    private void invalidateSession(String sessionId, Long userId) {
        if (StringUtils.hasText(sessionId)) {
            redisUtils.del(RedisConstants.SESSION_PREFIX + sessionId);
        }
        if (userId != null) {
            String activeSessionId = redisUtils.get(RedisConstants.ACTIVE_SESSION_PREFIX + userId);
            if (!StringUtils.hasText(sessionId) || sessionId.equals(activeSessionId)) {
                redisUtils.del(RedisConstants.ACTIVE_SESSION_PREFIX + userId);
            }
        }
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
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

    private void validateAvatar(MultipartFile avatarFile) {
        if (avatarFile == null || avatarFile.isEmpty()) {
            throw new BusinessException("请选择头像文件");
        }
        if (avatarFile.getSize() > 2 * 1024 * 1024) {
            throw new BusinessException("头像大小不能超过2MB");
        }
        String contentType = avatarFile.getContentType();
        if (contentType == null
                || !(contentType.equals("image/jpeg")
                || contentType.equals("image/png")
                || contentType.equals("image/webp"))) {
            throw new BusinessException("头像仅支持jpg、png、webp格式");
        }
    }
}
