package com.game.community.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.game.community.common.constant.ApiErrorCodes;
import com.game.community.common.constant.user.UserConstants;
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
import com.game.community.model.enums.user.UserStrings;
import com.game.community.model.payload.user.FieldAuditPayload;
import com.game.community.model.vo.user.ChangeEmailVO;
import com.game.community.model.vo.user.ProfileFieldSubmitVO;
import com.game.community.model.vo.user.SendCodeVO;
import com.game.community.model.vo.user.UserMeVO;
import com.game.community.user.mapper.UserAuthMapper;
import com.game.community.user.mapper.UserMapper;
import com.game.community.user.event.executor.AuditTaskExecutor;
import com.game.community.user.service.UserAccountService;
import com.game.community.user.common.audit.UserAuditHelper;
import com.game.community.user.service.UserProfileService;
import com.game.community.user.common.session.UserSessionHelper;
import com.game.community.user.common.verification.VerificationCodeHelper;
import com.game.community.utils.EncryptUtils;
import com.game.community.utils.MinIOUtils;
import com.game.community.utils.email.EmailValidator;
import com.game.community.utils.email.PasswordValidator;
import com.game.community.utils.ThreadLocal.UserThreadLocal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
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

    private final UserMapper userMapper;
    private final UserAuthMapper userAuthMapper;
    private final UserAuditHelper auditHelper;
    private final UserSessionHelper sessionHelper;
    private final MinIOUtils minIOUtils;
    private final AuditTaskExecutor auditTaskExecutor;
    private final VerificationCodeHelper verificationCodeHelper;
    private final UserAccountService userAccountService;
    private final TransactionTemplate transactionTemplate;

    /** 执行 getCurrentUser 对应的业务处理。 */
    @Override
    public Result<UserMeVO> getCurrentUser() {
        Long userId = UserThreadLocal.getUserId();
        if (userId == null) {
            throw new BusinessException(ApiErrorCodes.UNAUTHORIZED, "请先登录");
        }
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
        if (profileAudit.getUsernameAuditStatus() == null || !profileAudit.getUsernameAuditStatus().isBusy()) {
            vo.setUsernameAuditMessage(auditHelper.resolveLatestFieldAuditError(
                    userId, AuditFieldType.USERNAME));
        }
        if (profileAudit.getSignatureAuditStatus() == null || !profileAudit.getSignatureAuditStatus().isBusy()) {
            vo.setSignatureAuditMessage(auditHelper.resolveLatestFieldAuditError(
                    userId, AuditFieldType.SIGNATURE));
        }
        if (profileAudit.getAvatarAuditStatus() == null || !profileAudit.getAvatarAuditStatus().isBusy()) {
            vo.setAvatarAuditMessage(auditHelper.resolveLatestFieldAuditError(
                    userId, AuditFieldType.AVATAR));
        }
        return Result.success("查询成功", vo);
    }

    /** 执行 updateUsername 对应的业务处理。 */
    @Override
    public Result<ProfileFieldSubmitVO> updateUsername(UpdateUsernameDTO dto) {
        // 资料版本和审核占用共同实现乐观并发控制，避免覆盖别人的修改。
        Long userId = UserThreadLocal.getUserId();
        if (userId == null) {
            throw new BusinessException(ApiErrorCodes.UNAUTHORIZED, "请先登录");
        }
        User user = requireEditableUser(userId);
        if (!Objects.equals(user.getVersion(), dto.getVersion())) {
            throw new BusinessException(ApiErrorCodes.CONFLICT, "资料已更新，请刷新后重试");
        }
        UserProfileAudit profileAudit = auditHelper.getOrCreate(userId);
        String username = dto.getUsername().trim();
        int meaningfulLen = countMeaningfulChars(username);
        if (meaningfulLen < UserConstants.USERNAME_MIN_LENGTH
                || meaningfulLen > UserConstants.USERNAME_MAX_LENGTH) {
            throw new BusinessException(ApiErrorCodes.BAD_REQUEST, "昵称有效长度须在"
                    + UserConstants.USERNAME_MIN_LENGTH + "到" + UserConstants.USERNAME_MAX_LENGTH
                    + "个字符之间（空格不计）");
        }
        if (Objects.equals(username, user.getUsername())) {
            throw new BusinessException("昵称未变更");
        }
        if ((profileAudit.getUsernameAuditStatus() != null && profileAudit.getUsernameAuditStatus().isBusy())
                || auditHelper.hasInFlightTask(userId, AuditFieldType.USERNAME)) {
            throw new BusinessException(AuditFieldType.USERNAME.busyMessage());
        }

        Long taskId = transactionTemplate.execute(status -> {
            // 占用状态和审核任务必须在同一事务中写入，任一失败都释放占用。
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
        ProfileFieldSubmitVO vo = new ProfileFieldSubmitVO();
        vo.setTaskId(taskId);
        vo.setField(AuditFieldType.USERNAME);
        vo.setAuditStatus(FieldAuditStatus.AUDITING.getCode());
        vo.setPendingValue(username);
        return Result.success("昵称已提交审核", vo);
    }

    /** 执行 updateSignature 对应的业务处理。 */
    @Override
    public Result<ProfileFieldSubmitVO> updateSignature(UpdateSignatureDTO dto) {
        // 签名与用户名使用同样的版本校验和字段级审核占用规则。
        Long userId = UserThreadLocal.getUserId();
        if (userId == null) {
            throw new BusinessException(ApiErrorCodes.UNAUTHORIZED, "请先登录");
        }
        User user = requireEditableUser(userId);
        if (!Objects.equals(user.getVersion(), dto.getVersion())) {
            throw new BusinessException(ApiErrorCodes.CONFLICT, "资料已更新，请刷新后重试");
        }
        UserProfileAudit profileAudit = auditHelper.getOrCreate(userId);
        String signature = dto.getSignature() == null ? "" : dto.getSignature().trim();
        if (countMeaningfulChars(signature) > UserConstants.SIGNATURE_MAX_LENGTH) {
            throw new BusinessException(ApiErrorCodes.BAD_REQUEST, "个性签名有效长度不能超过"
                    + UserConstants.SIGNATURE_MAX_LENGTH + "个字符（空格/换行不计）");
        }
        if (Objects.equals(signature, UserStrings.orEmpty(user.getSignature()))) {
            throw new BusinessException("个性签名未变更");
        }
        if ((profileAudit.getSignatureAuditStatus() != null && profileAudit.getSignatureAuditStatus().isBusy())
                || auditHelper.hasInFlightTask(userId, AuditFieldType.SIGNATURE)) {
            throw new BusinessException(AuditFieldType.SIGNATURE.busyMessage());
        }

        Long taskId = transactionTemplate.execute(status -> {
            // 先 CAS 占用签名字段，再落审核任务，防止重复提交。
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
        ProfileFieldSubmitVO vo = new ProfileFieldSubmitVO();
        vo.setTaskId(taskId);
        vo.setField(AuditFieldType.SIGNATURE);
        vo.setAuditStatus(FieldAuditStatus.AUDITING.getCode());
        vo.setPendingValue(signature);
        return Result.success("签名已提交审核", vo);
    }

    /** 执行 uploadAvatar 对应的业务处理。 */
    @Override
    public Result<ProfileFieldSubmitVO> uploadAvatar(MultipartFile avatarFile, Integer version) {
        // 头像先存私有对象，审核通过后才发布为公开对象，避免未审核内容被访问。
        Long userId = UserThreadLocal.getUserId();
        if (userId == null) {
            throw new BusinessException(ApiErrorCodes.UNAUTHORIZED, "请先登录");
        }
        User user = requireEditableUser(userId);
        if (!Objects.equals(user.getVersion(), version)) {
            throw new BusinessException(ApiErrorCodes.CONFLICT, "资料已更新，请刷新后重试");
        }
        UserProfileAudit profileAudit = auditHelper.getOrCreate(userId);
        if ((profileAudit.getAvatarAuditStatus() != null && profileAudit.getAvatarAuditStatus().isBusy())
                || auditHelper.hasInFlightTask(userId, AuditFieldType.AVATAR)) {
            throw new BusinessException(AuditFieldType.AVATAR.busyMessage());
        }
        validateAvatar(avatarFile);

        String pendingObjectName = minIOUtils.uploadPrivateAvatar(avatarFile, avatarFile.getOriginalFilename());
        String previewUrl = minIOUtils.generatePrivateAvatarUrl(pendingObjectName);

        Long taskId;
        try {
            taskId = transactionTemplate.execute(status -> {
                // 文件上传在事务外完成，数据库失败时由 catch 删除私有对象补偿。
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
                    Map.of("pendingObjectName", pendingObjectName, "oldAvatarUrl", UserStrings.orEmpty(user.getAvatar())));
        } catch (BusinessException e) {
            // 线程池拒绝时任务已落库但不会执行，清理私有头像避免孤儿文件。
            try {
                minIOUtils.deletePrivateAvatar(pendingObjectName);
            } catch (Exception ignored) {
                // ignore
            }
            throw e;
        }
        ProfileFieldSubmitVO vo = new ProfileFieldSubmitVO();
        vo.setTaskId(taskId);
        vo.setField(AuditFieldType.AVATAR);
        vo.setAuditStatus(FieldAuditStatus.AUDITING.getCode());
        vo.setPendingValue(previewUrl);
        return Result.success("头像已提交审核", vo);
    }

    /** taskId 已在事务中落库；这里只提交任务，拒绝时由执行器回滚占用并记失败日志。 */
    private void enqueueOrReject(Long taskId, AuditFieldType taskType, Long userId, Object requestContent) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("taskId", taskId);
        snapshot.put("taskType", taskType);
        snapshot.put("userId", userId);
        snapshot.put("request", requestContent);
        snapshot.put("submitTime", LocalDateTime.now().toString());
        try {
            auditTaskExecutor.submit(taskId);
        } catch (RejectedExecutionException ex) {
            auditTaskExecutor.handleRejected(taskId, taskType, userId, snapshot, ex);
            throw new BusinessException(ApiErrorCodes.TOO_MANY_REQUESTS, "审核服务繁忙，请稍后重试");
        }
    }

    /** 执行 updateUserInfo 对应的业务处理。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> updateUserInfo(UpdateUserInfoDTO dto) {
        Long userId = UserThreadLocal.getUserId();
        if (userId == null) {
            throw new BusinessException(ApiErrorCodes.UNAUTHORIZED, "请先登录");
        }
        User user = requireEditableUser(userId);
        if (!Objects.equals(user.getVersion(), dto.getVersion())) {
            throw new BusinessException(ApiErrorCodes.CONFLICT, "资料已更新，请刷新后重试");
        }
        if (dto.getSteamAccount() == null) {
            return Result.success("资料更新成功");
        }
        String steamAccount = dto.getSteamAccount().trim();
        if (Objects.equals(steamAccount, UserStrings.orEmpty(user.getSteamAccount()))) {
            return Result.success("资料更新成功");
        }
        boolean updated = userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .eq(User::getVersion, user.getVersion())
                .set(User::getSteamAccount, steamAccount)
                .set(User::getVersion, user.getVersion() + 1)
                .set(User::getUpdateTime, LocalDateTime.now())) > 0;
        if (!updated) {
            throw new BusinessException(ApiErrorCodes.CONFLICT, "资料已被更新，请刷新后重试");
        }
        return Result.success("资料更新成功");
    }

    /** 执行 changePassword 对应的业务处理。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> changePassword(ChangePasswordDTO dto) {
        Long userId = UserThreadLocal.getUserId();
        if (userId == null) {
            throw new BusinessException(ApiErrorCodes.UNAUTHORIZED, "请先登录");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        try {
            PasswordValidator.assertValid(dto.getNewPassword());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ApiErrorCodes.BAD_REQUEST, e.getMessage());
        }
        if (!dto.getNewPassword().equals(dto.getConfirmPassword())) {
            throw new BusinessException(ApiErrorCodes.BAD_REQUEST, "两次输入的新密码不一致");
        }

        UserAuth userAuth = userAuthMapper.selectOne(new LambdaQueryWrapper<UserAuth>()
                .eq(UserAuth::getUserId, userId));
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

    /** 执行 sendChangeEmailOldCode 对应的业务处理。 */
    @Override
    public Result<SendCodeVO> sendChangeEmailOldCode() {
        Long userId = UserThreadLocal.getUserId();
        if (userId == null) {
            throw new BusinessException(ApiErrorCodes.UNAUTHORIZED, "请先登录");
        }
        User user = requireEditableUser(userId);
        String email = requireBoundEmail(user);
        int expireIn = verificationCodeHelper.sendEmailCode(email, CodeBizType.CHANGE_EMAIL_OLD);
        SendCodeVO vo = new SendCodeVO();
        vo.setExpireIn(expireIn);
        return Result.success("验证码发送任务已提交，请留意邮箱", vo);
    }

    /** 执行 prepareChangeEmail 对应的业务处理。 */
    @Override
    public Result<SendCodeVO> prepareChangeEmail(PrepareChangeEmailDTO dto) {
        Long userId = UserThreadLocal.getUserId();
        if (userId == null) {
            throw new BusinessException(ApiErrorCodes.UNAUTHORIZED, "请先登录");
        }
        User user = requireEditableUser(userId);
        String oldEmail = requireBoundEmail(user);
        String newEmail = validateChangeEmailTarget(oldEmail, dto.getNewEmail());

        verificationCodeHelper.assertCodeMatches(
                oldEmail, CodeBizType.CHANGE_EMAIL_OLD, dto.getOldCode());

        int expireIn = verificationCodeHelper.sendEmailCode(newEmail, CodeBizType.CHANGE_EMAIL_NEW);
        SendCodeVO vo = new SendCodeVO();
        vo.setExpireIn(expireIn);
        return Result.success("新邮箱验证码发送任务已提交，请留意邮箱", vo);
    }

    /** 执行 confirmChangeEmail 对应的业务处理。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<ChangeEmailVO> confirmChangeEmail(ConfirmChangeEmailDTO dto) {
        Long userId = UserThreadLocal.getUserId();
        if (userId == null) {
            throw new BusinessException(ApiErrorCodes.UNAUTHORIZED, "请先登录");
        }
        User user = requireEditableUser(userId);
        String oldEmail = requireBoundEmail(user);
        String newEmail = validateChangeEmailTarget(oldEmail, dto.getNewEmail());

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

    /** 执行 requireEditableUser 对应的业务处理。 */
    private User requireEditableUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        UserAccount account = userAccountService.refreshStatus(userId);
        userAccountService.assertEditable(account);
        return user;
    }

    /** 执行 validateChangeEmailTarget 对应的业务处理。 */
    private String validateChangeEmailTarget(String oldEmail, String rawNewEmail) {
        String newEmail = EmailValidator.normalize(rawNewEmail);
        if (oldEmail.equalsIgnoreCase(newEmail)) {
            throw new BusinessException(ApiErrorCodes.BAD_REQUEST, "新邮箱不能与当前邮箱相同");
        }
        if (userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getEmail, newEmail)) > 0) {
            throw new BusinessException(ApiErrorCodes.CONFLICT, "该邮箱已被注册");
        }
        return newEmail;
    }

    /** 执行 convertToMeVO 对应的业务处理。 */
    private UserMeVO convertToMeVO(User user, UserAccount account, UserProfileAudit profileAudit) {
        UserMeVO vo = new UserMeVO();
        BeanUtils.copyProperties(user, vo);
        vo.setStatus(account.getStatus().getCode());
        vo.setType(account.getType().getCode());
        vo.setUsernameAuditStatus((profileAudit.getUsernameAuditStatus() == null
                ? FieldAuditStatus.NONE : profileAudit.getUsernameAuditStatus()).getCode());
        vo.setSignatureAuditStatus((profileAudit.getSignatureAuditStatus() == null
                ? FieldAuditStatus.NONE : profileAudit.getSignatureAuditStatus()).getCode());
        vo.setAvatarAuditStatus((profileAudit.getAvatarAuditStatus() == null
                ? FieldAuditStatus.NONE : profileAudit.getAvatarAuditStatus()).getCode());
        vo.setFollowCount(0);
        vo.setFansCount(0);
        BeanUtils.copyProperties(account, vo, "status", "type", "version");
        return vo;
    }

    /** 执行 requireBoundEmail 对应的业务处理。 */
    private String requireBoundEmail(User user) {
        if (user == null || !StringUtils.hasText(user.getEmail())) {
            throw new BusinessException(ApiErrorCodes.BAD_REQUEST, "当前账号未绑定邮箱");
        }
        return EmailValidator.normalize(user.getEmail());
    }

    /** 执行 validateAvatar 对应的业务处理。 */
    private void validateAvatar(MultipartFile avatarFile) {
        if (avatarFile == null || avatarFile.isEmpty()) {
            throw new BusinessException("请选择头像文件");
        }
        if (avatarFile.getSize() > UserConstants.AVATAR_MAX_BYTES) {
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
