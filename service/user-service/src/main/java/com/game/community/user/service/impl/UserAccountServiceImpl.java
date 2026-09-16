package com.game.community.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.game.community.common.constant.ApiErrorCodes;
import com.game.community.common.constant.user.UserConstants;
import com.game.community.common.exception.BusinessException;
import com.game.community.model.base.Result;
import com.game.community.model.dto.user.CancelAccountDTO;
import com.game.community.model.entity.user.User;
import com.game.community.model.entity.user.UserAccount;
import com.game.community.model.entity.user.UserOperationLog;
import com.game.community.model.enums.user.CodeBizType;
import com.game.community.model.enums.user.OperationType;
import com.game.community.model.enums.user.UserAccountStatus;
import com.game.community.model.enums.user.UserStrings;
import com.game.community.model.vo.user.SendCodeVO;
import com.game.community.user.mapper.UserAccountMapper;
import com.game.community.user.mapper.UserMapper;
import com.game.community.user.mapper.UserOperationLogMapper;
import com.game.community.user.service.UserAccountService;
import com.game.community.user.common.session.UserSessionHelper;
import com.game.community.user.common.verification.VerificationCodeHelper;
import com.game.community.utils.ThreadLocal.UserThreadLocal;
import com.game.community.utils.email.EmailValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 用户账户生命周期服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserAccountServiceImpl implements UserAccountService {

    private final UserMapper userMapper;
    private final UserAccountMapper userAccountMapper;
    private final UserSessionHelper sessionHelper;
    private final VerificationCodeHelper verificationCodeHelper;
    private final UserOperationLogMapper userOperationLogMapper;

    /** 执行 cancelAccount 对应的业务处理。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> cancelAccount(CancelAccountDTO dto) {
        Long userId = UserThreadLocal.getUserId();
        if (userId == null) {
            throw new BusinessException(ApiErrorCodes.UNAUTHORIZED, "请先登录");
        }
        User user = userMapper.selectById(userId);
        if (user == null || !StringUtils.hasText(user.getEmail())) {
            throw new BusinessException("用户不存在");
        }
        verificationCodeHelper.verify(user.getEmail(), CodeBizType.CANCEL_ACCOUNT, dto.getCode());

        UserAccount account = refreshStatus(userId);
        if (account.getStatus() == UserAccountStatus.CANCELLING) {
            throw new BusinessException("账号已在注销中");
        }
        if (account.getStatus() == UserAccountStatus.CANCELLED) {
            throw new BusinessException("账号已注销");
        }
        if (account.getStatus() == UserAccountStatus.BANNED) {
            throw new BusinessException("账号被封禁中，无法申请注销");
        }

        LocalDateTime cancelAt = LocalDateTime.now().plusDays(UserConstants.CANCEL_COOLDOWN_DAYS);
        int updated = userAccountMapper.update(null, new LambdaUpdateWrapper<UserAccount>()
                .eq(UserAccount::getId, account.getId())
                .eq(UserAccount::getVersion, account.getVersion())
                .set(UserAccount::getStatus, UserAccountStatus.CANCELLING)
                .set(UserAccount::getCancelAt, cancelAt)
                .set(UserAccount::getVersion, account.getVersion() + 1)
                .set(UserAccount::getUpdateTime, LocalDateTime.now()));
        if (updated == 0) {
            throw new BusinessException(ApiErrorCodes.CONFLICT, "账号状态已变化，请刷新后重试");
        }

        sessionHelper.invalidateUserSession(userId);
        try {
            UserOperationLog logEntry = new UserOperationLog();
            logEntry.setUserId(userId);
            logEntry.setOperatorId(userId);
            logEntry.setOperation(OperationType.CANCEL_APPLY);
            logEntry.setDetail("cancelAt=" + cancelAt);
            logEntry.setIp(UserStrings.EMPTY);
            logEntry.setCreateTime(LocalDateTime.now());
            userOperationLogMapper.insert(logEntry);
        } catch (Exception e) {
            log.warn("记录操作日志失败: userId={}, operation={}", userId, OperationType.CANCEL_APPLY, e);
        }
        return Result.success("注销申请已提交");
    }

    /** 执行 sendCancelAccountCode 对应的业务处理。 */
    @Override
    public Result<SendCodeVO> sendCancelAccountCode() {
        Long userId = UserThreadLocal.getUserId();
        if (userId == null) {
            throw new BusinessException(ApiErrorCodes.UNAUTHORIZED, "请先登录");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        UserAccount account = refreshStatus(userId);
        assertEditable(account);
        if (!StringUtils.hasText(user.getEmail())) {
            throw new BusinessException(ApiErrorCodes.BAD_REQUEST, "当前账号未绑定邮箱");
        }
        String email = EmailValidator.normalize(user.getEmail());

        int expireIn = verificationCodeHelper.sendEmailCode(email, CodeBizType.CANCEL_ACCOUNT);
        SendCodeVO vo = new SendCodeVO();
        vo.setExpireIn(expireIn);
        return Result.success("验证码发送任务已提交，请留意邮箱", vo);
    }

    /** 执行 revokeCancel 对应的业务处理。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> revokeCancel() {
        Long userId = UserThreadLocal.getUserId();
        if (userId == null) {
            throw new BusinessException(ApiErrorCodes.UNAUTHORIZED, "请先登录");
        }
        doRevokeCancel(userId);
        return Result.success("已撤销注销");
    }

    /** 执行 revokeCancel 对应的业务处理。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void revokeCancel(Long userId) {
        doRevokeCancel(userId);
    }

    /** 执行 doRevokeCancel 对应的业务处理。 */
    private void doRevokeCancel(Long userId) {
        UserAccount account = refreshStatus(userId);
        if (account.getStatus() == UserAccountStatus.CANCELLED) {
            throw new BusinessException(ApiErrorCodes.FORBIDDEN, "账号已注销");
        }
        if (account.getStatus() != UserAccountStatus.CANCELLING) {
            throw new BusinessException("账号未在注销中");
        }

        int updated = userAccountMapper.update(null, new LambdaUpdateWrapper<UserAccount>()
                .eq(UserAccount::getId, account.getId())
                .eq(UserAccount::getVersion, account.getVersion())
                .set(UserAccount::getStatus, UserAccountStatus.NORMAL)
                .set(UserAccount::getCancelAt, null)
                .set(UserAccount::getVersion, account.getVersion() + 1)
                .set(UserAccount::getUpdateTime, LocalDateTime.now()));
        if (updated == 0) {
            throw new BusinessException(ApiErrorCodes.CONFLICT, "账号状态已变化，请刷新后重试");
        }

        try {
            UserOperationLog logEntry = new UserOperationLog();
            logEntry.setUserId(userId);
            logEntry.setOperatorId(userId);
            logEntry.setOperation(OperationType.CANCEL_REVOKE);
            logEntry.setDetail(UserStrings.EMPTY);
            logEntry.setIp(UserStrings.EMPTY);
            logEntry.setCreateTime(LocalDateTime.now());
            userOperationLogMapper.insert(logEntry);
        } catch (Exception e) {
            log.warn("记录操作日志失败: userId={}, operation={}", userId, OperationType.CANCEL_REVOKE, e);
        }
    }

    /** 执行 banUser 对应的业务处理。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> banUser(Long userId, String reason, Integer durationHours, Long operatorId) {
        if (!StringUtils.hasText(reason)) {
            throw new BusinessException(ApiErrorCodes.BAD_REQUEST, "封禁原因不能为空");
        }
        if (durationHours != null && durationHours < 0) {
            throw new BusinessException(ApiErrorCodes.BAD_REQUEST, "封禁时长不能为负数");
        }
        Long operator = operatorId;
        if (operator == null) {
            operator = UserThreadLocal.getUserId();
            if (operator == null) {
                throw new BusinessException(ApiErrorCodes.UNAUTHORIZED, "请先登录");
            }
        }
        UserAccount account = refreshStatus(userId);
        if (account.getStatus() == UserAccountStatus.BANNED) {
            throw new BusinessException("账号已被封禁");
        }
        if (account.getStatus() == UserAccountStatus.CANCELLED) {
            throw new BusinessException("账号已注销，无法封禁");
        }

        LocalDateTime banUntil = (durationHours != null && durationHours > 0)
                ? LocalDateTime.now().plusHours(durationHours)
                : null;

        int updated = userAccountMapper.update(null, new LambdaUpdateWrapper<UserAccount>()
                .eq(UserAccount::getId, account.getId())
                .eq(UserAccount::getVersion, account.getVersion())
                .set(UserAccount::getStatus, UserAccountStatus.BANNED)
                .set(UserAccount::getBanUntil, banUntil)
                .set(UserAccount::getBanReason, reason.trim())
                .set(UserAccount::getVersion, account.getVersion() + 1)
                .set(UserAccount::getUpdateTime, LocalDateTime.now()));
        if (updated == 0) {
            throw new BusinessException(ApiErrorCodes.CONFLICT, "账号状态已变化，请刷新后重试");
        }

        sessionHelper.invalidateUserSession(userId);

        try {
            UserOperationLog logEntry = new UserOperationLog();
            logEntry.setUserId(userId);
            logEntry.setOperatorId(operator);
            logEntry.setOperation(OperationType.BAN);
            logEntry.setDetail("reason=" + reason + ", durationHours=" + durationHours
                    + ", operatorId=" + operator);
            logEntry.setIp(UserStrings.EMPTY);
            logEntry.setCreateTime(LocalDateTime.now());
            userOperationLogMapper.insert(logEntry);
        } catch (Exception e) {
            log.warn("记录操作日志失败: userId={}, operation={}", userId, OperationType.BAN, e);
        }
        return Result.success("封禁成功");
    }

    /** 执行 banUserByAccountId 对应的业务处理。 */
    @Override
    public Result<Void> banUserByAccountId(Long accountId, String reason, Integer durationHours, Long operatorId) {
        return banUser(requireUserIdByAccountId(accountId), reason, durationHours, operatorId);
    }

    /** 执行 unbanUser 对应的业务处理。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> unbanUser(Long userId, Long operatorId) {
        Long operator = operatorId;
        if (operator == null) {
            operator = UserThreadLocal.getUserId();
            if (operator == null) {
                throw new BusinessException(ApiErrorCodes.UNAUTHORIZED, "请先登录");
            }
        }
        UserAccount account = refreshStatus(userId);
        if (account.getStatus() != UserAccountStatus.BANNED) {
            throw new BusinessException("账号未被封禁");
        }

        if (!clearBan(account)) {
            throw new BusinessException(ApiErrorCodes.CONFLICT, "账号状态已变化，请刷新后重试");
        }
        try {
            UserOperationLog logEntry = new UserOperationLog();
            logEntry.setUserId(userId);
            logEntry.setOperatorId(operator);
            logEntry.setOperation(OperationType.UNBAN);
            logEntry.setDetail("operatorId=" + operator);
            logEntry.setIp(UserStrings.EMPTY);
            logEntry.setCreateTime(LocalDateTime.now());
            userOperationLogMapper.insert(logEntry);
        } catch (Exception e) {
            log.warn("记录操作日志失败: userId={}, operation={}", userId, OperationType.UNBAN, e);
        }
        return Result.success("解封成功");
    }

    /** 执行 unbanUserByAccountId 对应的业务处理。 */
    @Override
    public Result<Void> unbanUserByAccountId(Long accountId, Long operatorId) {
        return unbanUser(requireUserIdByAccountId(accountId), operatorId);
    }

    /** 执行 refreshStatus 对应的业务处理。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserAccount refreshStatus(Long userId) {
        return refreshStatus(getAccount(userId), true);
    }

    /** 执行 refreshStatusForToken 对应的业务处理。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserAccount refreshStatusForToken(Long userId) {
        return refreshStatus(getAccount(userId), false);
    }

    /** 执行 refreshStatus 对应的业务处理。 */
    private UserAccount refreshStatus(UserAccount account, boolean invalidateSession) {
        if (account == null) {
            throw new BusinessException("用户账号数据异常");
        }
        LocalDateTime now = LocalDateTime.now();

        if (account.getStatus() == UserAccountStatus.BANNED
                && account.getBanUntil() != null
                && !account.getBanUntil().isAfter(now)) {
            clearBan(account);
            log.info("被动解封: userId={}", account.getUserId());
            return getAccount(account.getUserId());
        }

        if (account.getStatus() == UserAccountStatus.CANCELLING
                && account.getCancelAt() != null
                && !account.getCancelAt().isAfter(now)) {
            completeCancellation(account, invalidateSession);
            log.info("被动完成注销: userId={}", account.getUserId());
            return getAccount(account.getUserId());
        }

        return account;
    }

    /** 执行 assertLoginAllowed 对应的业务处理。 */
    @Override
    public void assertLoginAllowed(UserAccount account) {
        if (account.getStatus() == UserAccountStatus.CANCELLED) {
            throw new BusinessException(ApiErrorCodes.FORBIDDEN, "账号已注销");
        }
        if (account.getStatus() == UserAccountStatus.BANNED) {
            String msg = account.getBanUntil() != null
                    ? "账号被封禁至" + account.getBanUntil() + "，原因：" + account.getBanReason()
                    : "账号已被永久封禁，原因：" + account.getBanReason();
            throw new BusinessException(ApiErrorCodes.FORBIDDEN, msg);
        }
    }

    /** 执行 assertEditable 对应的业务处理。 */
    @Override
    public void assertEditable(UserAccount account) {
        assertLoginAllowed(account);
        if (account.getStatus() == UserAccountStatus.CANCELLING) {
            throw new BusinessException(ApiErrorCodes.FORBIDDEN, "账号注销冷静期中，无法修改资料");
        }
    }

    /** 执行 clearBan 对应的业务处理。 */
    private boolean clearBan(UserAccount account) {
        int updated = userAccountMapper.update(null, new LambdaUpdateWrapper<UserAccount>()
                .eq(UserAccount::getId, account.getId())
                .eq(UserAccount::getVersion, account.getVersion())
                .set(UserAccount::getStatus, UserAccountStatus.NORMAL)
                .set(UserAccount::getBanUntil, null)
                .set(UserAccount::getBanReason, UserStrings.EMPTY)
                .set(UserAccount::getVersion, account.getVersion() + 1)
                .set(UserAccount::getUpdateTime, LocalDateTime.now()));
        if (updated == 0) {
            return false;
        }
        account.setStatus(UserAccountStatus.NORMAL);
        account.setBanUntil(null);
        account.setBanReason(UserStrings.EMPTY);
        account.setVersion(account.getVersion() + 1);
        return true;
    }

    /** 执行 completeCancellation 对应的业务处理。 */
    private boolean completeCancellation(UserAccount account, boolean invalidateSession) {
        int updated = userAccountMapper.update(null, new LambdaUpdateWrapper<UserAccount>()
                .eq(UserAccount::getId, account.getId())
                .eq(UserAccount::getVersion, account.getVersion())
                .eq(UserAccount::getStatus, UserAccountStatus.CANCELLING)
                .set(UserAccount::getStatus, UserAccountStatus.CANCELLED)
                .set(UserAccount::getVersion, account.getVersion() + 1)
                .set(UserAccount::getUpdateTime, LocalDateTime.now()));
        if (updated == 0) {
            return false;
        }

        User user = userMapper.selectById(account.getUserId());
        if (user == null) {
            throw new BusinessException(ApiErrorCodes.INTERNAL_ERROR, "用户资料数据异常");
        }
        int userUpdated = userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, user.getId())
                .eq(User::getVersion, user.getVersion())
                .set(User::getEmail, UserConstants.CANCELLED_EMAIL_PREFIX + user.getId()
                        + UserConstants.INVALID_EMAIL_DOMAIN)
                .set(User::getVersion, user.getVersion() + 1)
                .set(User::getDeleted, UserConstants.DELETED)
                .set(User::getUpdateTime, LocalDateTime.now()));
        if (userUpdated == 0) {
            // 账户状态和用户资料必须在同一事务中完成，不能留下半注销数据。
            throw new BusinessException(ApiErrorCodes.CONFLICT, "用户资料已变化，请稍后重试");
        }

        if (invalidateSession) {
            sessionHelper.invalidateUserSession(account.getUserId());
        }
        try {
            UserOperationLog logEntry = new UserOperationLog();
            logEntry.setUserId(account.getUserId());
            logEntry.setOperatorId(account.getUserId());
            logEntry.setOperation(OperationType.CANCEL_COMPLETE);
            logEntry.setDetail(UserStrings.EMPTY);
            logEntry.setIp(UserStrings.EMPTY);
            logEntry.setCreateTime(LocalDateTime.now());
            userOperationLogMapper.insert(logEntry);
        } catch (Exception e) {
            log.warn("记录操作日志失败: userId={}, operation={}", account.getUserId(), OperationType.CANCEL_COMPLETE, e);
        }
        return true;
    }

    /** 执行 getAccount 对应的业务处理。 */
    private UserAccount getAccount(Long userId) {
        UserAccount account = userAccountMapper.selectOne(new LambdaQueryWrapper<UserAccount>()
                .eq(UserAccount::getUserId, userId));
        if (account == null) {
            throw new BusinessException("用户账号数据异常");
        }
        return account;
    }

    /** 执行 requireUserIdByAccountId 对应的业务处理。 */
    private Long requireUserIdByAccountId(Long accountId) {
        if (accountId == null) {
            throw new BusinessException("账号ID不能为空");
        }
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getAccountId, accountId));
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return user.getId();
    }

    /** 执行 updateSteamAccount 对应的业务处理。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSteamAccount(Long userId, String steamAccount) {
        if (userId == null) {
            return;
        }
        String value = steamAccount == null ? "" : steamAccount;
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        int updated = userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .eq(User::getVersion, user.getVersion())
                .set(User::getSteamAccount, value)
                .set(User::getVersion, user.getVersion() + 1)
                .set(User::getUpdateTime, LocalDateTime.now()));
        if (updated == 0) {
            throw new BusinessException(ApiErrorCodes.CONFLICT, "用户资料已变化，请重试");
        }
    }
}
