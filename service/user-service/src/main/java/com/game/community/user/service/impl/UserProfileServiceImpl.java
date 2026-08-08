package com.game.community.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.game.community.common.constant.ApiErrorCodes;
import com.game.community.common.exception.BusinessException;
import com.game.community.model.base.Result;
import com.game.community.model.dto.user.ChangePasswordDTO;
import com.game.community.model.dto.user.ConfirmChangeEmailDTO;
import com.game.community.model.dto.user.PrepareChangeEmailDTO;
import com.game.community.model.dto.user.UpdateSignatureDTO;
import com.game.community.model.dto.user.UpdateUserInfoDTO;
import com.game.community.model.dto.user.UpdateUsernameDTO;
import com.game.community.model.entity.user.User;
import com.game.community.model.entity.user.UserAccount;
import com.game.community.model.entity.user.UserAuth;
import com.game.community.model.entity.user.UserProfileAudit;
import com.game.community.model.enums.user.AuditFieldType;
import com.game.community.model.enums.user.CodeBizType;
import com.game.community.model.enums.user.FieldAuditStatus;
import com.game.community.model.payload.user.FieldAuditPayload;
import com.game.community.model.vo.user.ChangeEmailVO;
import com.game.community.model.vo.user.ProfileFieldSubmitVO;
import com.game.community.model.vo.user.SendCodeVO;
import com.game.community.model.vo.user.UserMeVO;
import com.game.community.user.mapper.UserAuthMapper;
import com.game.community.user.mapper.UserMapper;
import com.game.community.user.service.UserAccountService;
import com.game.community.user.audit.UserAuditHelper;
import com.game.community.user.service.UserFieldAuditTaskService;
import com.game.community.user.service.UserProfileService;
import com.game.community.user.common.session.UserSessionHelper;
import com.game.community.user.common.support.UserSupport;
import com.game.community.user.common.verification.VerificationCodeHelper;
import com.game.community.utils.EncryptUtils;
import com.game.community.utils.MinIOUtils;
import com.game.community.utils.email.EmailValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.RejectedExecutionException;

/**
 * 用户资料服务实现（字段级独立审核）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserProfileServiceImpl implements UserProfileService {

    private static final long AVATAR_MAX_BYTES = 5L * 1024 * 1024;

    private final UserMapper userMapper;
    private final UserAuthMapper userAuthMapper;
    private final UserAuditHelper auditHelper;
    private final UserSessionHelper sessionHelper;
    private final MinIOUtils minIOUtils;
    private final UserFieldAuditTaskService fieldAuditTaskService;
    private final VerificationCodeHelper verificationCodeHelper;
    private final UserAccountService userAccountService;
    private final TransactionTemplate transactionTemplate;
    private final UserSupport userSupport;

    @Override
    public Result<UserMeVO> getCurrentUser() {
        Long userId = userSupport.requireUserId();
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        UserAccount account = userAccountService.refreshStatus(userId);
        UserProfileAudit profileAudit = auditHelper.getOrCreate(userId);
        UserMeVO vo = convertToMeVO(user, account, profileAudit);
        vo.setPendingUsername(profileAudit.getPendingUsername());
        vo.setPendingSignature(profileAudit.getPendingSignature());
        vo.setPendingAvatarUrl(auditHelper.resolvePendingAvatarUrl(profileAudit));
        if (!auditHelper.isFieldBusy(profileAudit.getUsernameAuditStatus())) {
            vo.setUsernameAuditMessage(auditHelper.resolveLatestFieldAuditError(
                    userId, AuditFieldType.USERNAME));
        }
        if (!auditHelper.isFieldBusy(profileAudit.getSignatureAuditStatus())) {
            vo.setSignatureAuditMessage(auditHelper.resolveLatestFieldAuditError(
                    userId, AuditFieldType.SIGNATURE));
        }
        if (!auditHelper.isFieldBusy(profileAudit.getAvatarAuditStatus())) {
            vo.setAvatarAuditMessage(auditHelper.resolveLatestFieldAuditError(
                    userId, AuditFieldType.AVATAR));
        }
        return Result.success("查询成功", vo);
    }

    @Override
    public Result<ProfileFieldSubmitVO> updateUsername(UpdateUsernameDTO dto) {
        Long userId = userSupport.requireUserId();
        User user = requireEditableUser(userId);
        assertVersion(user, dto.getVersion());
        UserProfileAudit profileAudit = auditHelper.getOrCreate(userId);
        String username = dto.getUsername().trim();
        int meaningfulLen = countMeaningfulChars(username);
        if (meaningfulLen < 2 || meaningfulLen > 20) {
            throw new BusinessException(ApiErrorCodes.BAD_REQUEST, "昵称有效长度须在2到20个字符之间（空格不计）");
        }
        if (Objects.equals(username, user.getUsername())) {
            throw new BusinessException("昵称未变更");
        }
        if (auditHelper.isFieldBusy(profileAudit.getUsernameAuditStatus())
                || auditHelper.hasInFlightTask(userId, AuditFieldType.USERNAME)) {
            throw new BusinessException(AuditFieldType.USERNAME.busyMessage());
        }

        Long taskId = transactionTemplate.execute(status -> {
            if (!auditHelper.acquireUsernameAudit(userId, username)) {
                throw new BusinessException(AuditFieldType.USERNAME.busyMessage());
            }
            try {
                FieldAuditPayload payload = new FieldAuditPayload();
                payload.setField(AuditFieldType.USERNAME);
                payload.setContent(username);
                payload.setUserVersion(user.getVersion());
                return auditHelper.createFieldAuditTask(
                        userId, AuditFieldType.USERNAME, username, payload);
            } catch (RuntimeException e) {
                auditHelper.clearUsernameAudit(userId, username);
                throw e;
            }
        });

        enqueueOrReject(taskId, AuditFieldType.USERNAME, userId, username);
        return Result.success("昵称已提交审核",
                buildSubmitVO(taskId, AuditFieldType.USERNAME, username));
    }

    @Override
    public Result<ProfileFieldSubmitVO> updateSignature(UpdateSignatureDTO dto) {
        Long userId = userSupport.requireUserId();
        User user = requireEditableUser(userId);
        assertVersion(user, dto.getVersion());
        UserProfileAudit profileAudit = auditHelper.getOrCreate(userId);
        String signature = dto.getSignature() == null ? "" : dto.getSignature().trim();
        if (countMeaningfulChars(signature) > 50) {
            throw new BusinessException(ApiErrorCodes.BAD_REQUEST, "个性签名有效长度不能超过50个字符（空格/换行不计）");
        }
        if (Objects.equals(signature, nullToEmpty(user.getSignature()))) {
            throw new BusinessException("个性签名未变更");
        }
        if (auditHelper.isFieldBusy(profileAudit.getSignatureAuditStatus())
                || auditHelper.hasInFlightTask(userId, AuditFieldType.SIGNATURE)) {
            throw new BusinessException(AuditFieldType.SIGNATURE.busyMessage());
        }

        Long taskId = transactionTemplate.execute(status -> {
            if (!auditHelper.acquireSignatureAudit(userId, signature)) {
                throw new BusinessException(AuditFieldType.SIGNATURE.busyMessage());
            }
            try {
                FieldAuditPayload payload = new FieldAuditPayload();
                payload.setField(AuditFieldType.SIGNATURE);
                payload.setContent(signature);
                payload.setUserVersion(user.getVersion());
                return auditHelper.createFieldAuditTask(
                        userId, AuditFieldType.SIGNATURE, signature, payload);
            } catch (RuntimeException e) {
                auditHelper.clearSignatureAudit(userId, signature);
                throw e;
            }
        });

        enqueueOrReject(taskId, AuditFieldType.SIGNATURE, userId, signature);
        return Result.success("签名已提交审核",
                buildSubmitVO(taskId, AuditFieldType.SIGNATURE, signature));
    }

    @Override
    public Result<ProfileFieldSubmitVO> uploadAvatar(MultipartFile avatarFile, Integer version) {
        Long userId = userSupport.requireUserId();
        User user = requireEditableUser(userId);
        assertVersion(user, version);
        UserProfileAudit profileAudit = auditHelper.getOrCreate(userId);
        if (auditHelper.isFieldBusy(profileAudit.getAvatarAuditStatus())
                || auditHelper.hasInFlightTask(userId, AuditFieldType.AVATAR)) {
            throw new BusinessException(AuditFieldType.AVATAR.busyMessage());
        }
        validateAvatar(avatarFile);

        String pendingObjectName = minIOUtils.uploadPrivateAvatar(avatarFile, avatarFile.getOriginalFilename());
        String previewUrl = minIOUtils.generatePrivateAvatarUrl(pendingObjectName);

        Long taskId;
        try {
            taskId = transactionTemplate.execute(status -> {
                if (!auditHelper.acquireAvatarAudit(userId, pendingObjectName)) {
                    throw new BusinessException(AuditFieldType.AVATAR.busyMessage());
                }
                try {
                    FieldAuditPayload payload = new FieldAuditPayload();
                    payload.setField(AuditFieldType.AVATAR);
                    payload.setOldAvatarUrl(user.getAvatar());
                    payload.setPendingObjectName(pendingObjectName);
                    payload.setUserVersion(user.getVersion());
                    return auditHelper.createFieldAuditTask(
                            userId, AuditFieldType.AVATAR, pendingObjectName, payload);
                } catch (RuntimeException e) {
                    auditHelper.clearAvatarAudit(userId, pendingObjectName);
                    throw e;
                }
            });
        } catch (RuntimeException e) {
            try {
                minIOUtils.deletePrivateAvatar(pendingObjectName);
            } catch (Exception ignored) {
                // ignore cleanup failure
            }
            throw e;
        }

        try {
            enqueueOrReject(taskId, AuditFieldType.AVATAR, userId,
                    Map.of("pendingObjectName", pendingObjectName, "oldAvatarUrl", nullToEmpty(user.getAvatar())));
        } catch (BusinessException e) {
            try {
                minIOUtils.deletePrivateAvatar(pendingObjectName);
            } catch (Exception ignored) {
                // ignore
            }
            throw e;
        }
        return Result.success("头像已提交审核",
                buildSubmitVO(taskId, AuditFieldType.AVATAR, previewUrl));
    }

    private void enqueueOrReject(Long taskId, AuditFieldType taskType, Long userId, Object requestContent) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("taskId", taskId);
        snapshot.put("taskType", taskType);
        snapshot.put("userId", userId);
        snapshot.put("request", requestContent);
        snapshot.put("submitTime", LocalDateTime.now().toString());
        try {
            fieldAuditTaskService.enqueueFieldAudit(taskId);
        } catch (RejectedExecutionException ex) {
            fieldAuditTaskService.handleEnqueueRejected(taskId, taskType, userId, snapshot, ex);
            throw new BusinessException(ApiErrorCodes.TOO_MANY_REQUESTS, "审核服务繁忙，请稍后重试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> updateUserInfo(UpdateUserInfoDTO dto) {
        Long userId = userSupport.requireUserId();
        User user = requireEditableUser(userId);
        assertVersion(user, dto.getVersion());
        if (dto.getSteamAccount() == null) {
            return Result.success("资料更新成功");
        }
        String steamAccount = dto.getSteamAccount().trim();
        if (Objects.equals(steamAccount, nullToEmpty(user.getSteamAccount()))) {
            return Result.success("资料更新成功");
        }
        boolean updated = userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .set(User::getSteamAccount, steamAccount)
                .set(User::getUpdateTime, LocalDateTime.now())) > 0;
        if (!updated) {
            throw new BusinessException("资料更新失败，请重试");
        }
        return Result.success("资料更新成功");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> changePassword(ChangePasswordDTO dto) {
        Long userId = userSupport.requireUserId();
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        UserSupport.assertValidPassword(dto.getNewPassword());
        if (!dto.getNewPassword().equals(dto.getConfirmPassword())) {
            throw new BusinessException(ApiErrorCodes.BAD_REQUEST, "两次输入的新密码不一致");
        }

        UserAuth userAuth = userSupport.getUserAuth(userId);
        if (userAuth == null) {
            throw new BusinessException("用户认证数据异常");
        }

        if (!EncryptUtils.bcryptCheck(dto.getOldPassword(), userAuth.getPassword())) {
            throw new BusinessException("原密码错误");
        }
        if (dto.getOldPassword().equals(dto.getNewPassword())) {
            throw new BusinessException("新密码不能与原密码相同");
        }

        boolean updated = userAuthMapper.update(null, new LambdaUpdateWrapper<UserAuth>()
                .eq(UserAuth::getUserId, userId)
                .eq(UserAuth::getVersion, userAuth.getVersion())
                .set(UserAuth::getSalt, "")
                .set(UserAuth::getPassword, EncryptUtils.bcryptEncode(dto.getNewPassword()))
                .set(UserAuth::getFailCount, 0)
                .set(UserAuth::getLockUntil, null)
                .set(UserAuth::getLastPasswordChange, LocalDateTime.now())
                .set(UserAuth::getVersion, userAuth.getVersion() + 1)
                .set(UserAuth::getUpdateTime, LocalDateTime.now())) > 0;
        if (!updated) {
            throw new BusinessException("密码已被更新，请刷新后重试");
        }
        log.info("密码修改成功，已使用BCrypt加密: userId={}", userId);
        sessionHelper.invalidateUserSession(userId);
        return Result.success("密码修改成功，请重新登录");
    }

    @Override
    public Result<SendCodeVO> sendChangeEmailOldCode() {
        Long userId = userSupport.requireUserId();
        User user = requireEditableUser(userId);
        String email = requireBoundEmail(user);
        int expireIn = verificationCodeHelper.sendEmailCode(email, CodeBizType.CHANGE_EMAIL_OLD);
        SendCodeVO vo = new SendCodeVO();
        vo.setExpireIn(expireIn);
        return Result.success("验证码已发送", vo);
    }

    @Override
    public Result<SendCodeVO> prepareChangeEmail(PrepareChangeEmailDTO dto) {
        Long userId = userSupport.requireUserId();
        User user = requireEditableUser(userId);
        String oldEmail = requireBoundEmail(user);
        String newEmail = validateChangeEmailTarget(user, dto.getNewEmail());

        verificationCodeHelper.assertCodeMatches(
                oldEmail, CodeBizType.CHANGE_EMAIL_OLD, dto.getOldCode());

        int expireIn = verificationCodeHelper.sendEmailCode(newEmail, CodeBizType.CHANGE_EMAIL_NEW);
        SendCodeVO vo = new SendCodeVO();
        vo.setExpireIn(expireIn);
        return Result.success("新邮箱验证码已发送", vo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<ChangeEmailVO> confirmChangeEmail(ConfirmChangeEmailDTO dto) {
        Long userId = userSupport.requireUserId();
        User user = requireEditableUser(userId);
        String oldEmail = requireBoundEmail(user);
        String newEmail = validateChangeEmailTarget(user, dto.getNewEmail());

        verificationCodeHelper.verify(
                oldEmail, CodeBizType.CHANGE_EMAIL_OLD, dto.getOldCode());
        verificationCodeHelper.verify(
                newEmail, CodeBizType.CHANGE_EMAIL_NEW, dto.getNewCode());

        try {
            boolean updated = userMapper.update(null, new LambdaUpdateWrapper<User>()
                    .eq(User::getId, userId)
                    .eq(User::getVersion, user.getVersion())
                    .set(User::getEmail, newEmail)
                    .set(User::getVersion, user.getVersion() + 1)
                    .set(User::getUpdateTime, LocalDateTime.now())) > 0;
            if (!updated) {
                throw new BusinessException("邮箱更新失败，请重试");
            }
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ApiErrorCodes.CONFLICT, "该邮箱已被注册");
        }

        sessionHelper.invalidateUserSession(userId);
        log.info("邮箱修改成功: userId={}, email={}", userId, newEmail);

        ChangeEmailVO vo = new ChangeEmailVO();
        vo.setEmail(newEmail);
        return Result.success("邮箱修改成功，请重新登录", vo);
    }

    private User requireEditableUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        UserAccount account = userAccountService.refreshStatus(userId);
        userAccountService.assertEditable(account);
        return user;
    }

    private void assertVersion(User user, Integer expectedVersion) {
        if (!Objects.equals(user.getVersion(), expectedVersion)) {
            throw new BusinessException(ApiErrorCodes.CONFLICT, "资料已更新，请刷新后重试");
        }
    }

    private ProfileFieldSubmitVO buildSubmitVO(Long taskId, AuditFieldType field, String pendingValue) {
        ProfileFieldSubmitVO vo = new ProfileFieldSubmitVO();
        vo.setTaskId(taskId);
        vo.setField(field);
        vo.setAuditStatus(FieldAuditStatus.AUDITING.getCode());
        vo.setPendingValue(pendingValue);
        return vo;
    }

    private String validateChangeEmailTarget(User user, String rawNewEmail) {
        String oldEmail = requireBoundEmail(user);
        String newEmail = EmailValidator.normalize(rawNewEmail);
        if (oldEmail.equalsIgnoreCase(newEmail)) {
            throw new BusinessException(ApiErrorCodes.BAD_REQUEST, "新邮箱不能与当前邮箱相同");
        }
        if (userSupport.existsByEmail(newEmail)) {
            throw new BusinessException(ApiErrorCodes.CONFLICT, "该邮箱已被注册");
        }
        return newEmail;
    }

    private UserMeVO convertToMeVO(User user, UserAccount account, UserProfileAudit profileAudit) {
        UserMeVO vo = new UserMeVO();
        vo.setAccountId(user.getAccountId());
        vo.setVersion(user.getVersion());
        vo.setUsername(user.getUsername());
        vo.setAvatar(user.getAvatar());
        vo.setSignature(user.getSignature());
        vo.setEmail(user.getEmail());
        vo.setStatus(account.getStatus().getCode());
        vo.setType(account.getType().getCode());
        vo.setSteamAccount(user.getSteamAccount());
        vo.setUsernameAuditStatus(fieldStatusCode(profileAudit.getUsernameAuditStatus()));
        vo.setSignatureAuditStatus(fieldStatusCode(profileAudit.getSignatureAuditStatus()));
        vo.setAvatarAuditStatus(fieldStatusCode(profileAudit.getAvatarAuditStatus()));
        vo.setFollowCount(0);
        vo.setFansCount(0);
        vo.setBanUntil(account.getBanUntil());
        vo.setBanReason(account.getBanReason());
        return vo;
    }

    private String requireBoundEmail(User user) {
        if (user == null || !StringUtils.hasText(user.getEmail())) {
            throw new BusinessException(ApiErrorCodes.BAD_REQUEST, "当前账号未绑定邮箱");
        }
        return EmailValidator.normalize(user.getEmail());
    }

    private int fieldStatusCode(FieldAuditStatus status) {
        return (status == null ? FieldAuditStatus.NONE : status).getCode();
    }

    private void validateAvatar(MultipartFile avatarFile) {
        if (avatarFile == null || avatarFile.isEmpty()) {
            throw new BusinessException("请选择头像文件");
        }
        if (avatarFile.getSize() > AVATAR_MAX_BYTES) {
            throw new BusinessException("头像大小不能超过5MB");
        }
        String contentType = avatarFile.getContentType();
        if (contentType == null
                || !(contentType.equals("image/jpeg")
                || contentType.equals("image/png")
                || contentType.equals("image/webp"))) {
            throw new BusinessException("头像仅支持jpg、png、webp格式");
        }
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /** 空格、换行等空白不计有效字符 */
    private static int countMeaningfulChars(String value) {
        if (value == null || value.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isWhitespace(value.charAt(i))) {
                count++;
            }
        }
        return count;
    }
}
