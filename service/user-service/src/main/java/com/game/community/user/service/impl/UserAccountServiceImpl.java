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
import com.game.community.model.enums.user.CodeBizType;
import com.game.community.model.enums.user.OperationType;
import com.game.community.model.enums.user.UserAccountStatus;
import com.game.community.model.enums.user.UserStrings;
import com.game.community.model.vo.user.SendCodeVO;
import com.game.community.user.mapper.UserAccountMapper;
import com.game.community.user.mapper.UserMapper;
import com.game.community.user.service.UserAccountService;
import com.game.community.user.common.session.UserSessionHelper;
import com.game.community.user.common.support.UserSupport;
import com.game.community.user.common.verification.VerificationCodeHelper;
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
    private final UserSupport userSupport;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> cancelAccount(CancelAccountDTO dto) {
        Long userId = userSupport.requireUserId();
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
        userSupport.logOperation(userId, OperationType.CANCEL_APPLY,
                "cancelAt=" + cancelAt, null);
        return Result.success("注销申请已提交");
    }

    @Override
    public Result<SendCodeVO> sendCancelAccountCode() {
        Long userId = userSupport.requireUserId();
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        UserAccount account = refreshStatus(userId);
        assertEditable(account);
        String email = requireBoundEmail(user);

        int expireIn = verificationCodeHelper.sendEmailCode(email, CodeBizType.CANCEL_ACCOUNT);
        SendCodeVO vo = new SendCodeVO();
        vo.setExpireIn(expireIn);
        return Result.success("验证码已发送", vo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> revokeCancel() {
        Long userId = userSupport.requireUserId();
        doRevokeCancel(userId);
        return Result.success("已撤销注销");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void revokeCancel(Long userId) {
        doRevokeCancel(userId);
    }

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

        userSupport.logOperation(userId, OperationType.CANCEL_REVOKE, null, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> banUser(Long userId, String reason, Integer durationHours, Long operatorId) {
        Long operator = operatorId != null ? operatorId : userSupport.requireUserId();
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
                .set(UserAccount::getBanReason, reason)
                .set(UserAccount::getVersion, account.getVersion() + 1)
                .set(UserAccount::getUpdateTime, LocalDateTime.now()));
        if (updated == 0) {
            throw new BusinessException(ApiErrorCodes.CONFLICT, "账号状态已变化，请刷新后重试");
        }

        sessionHelper.invalidateUserSession(userId);

        userSupport.logOperation(userId, OperationType.BAN,
                "reason=" + reason + ", durationHours=" + durationHours
                        + ", operatorId=" + operator, null);
        return Result.success("封禁成功");
    }

    @Override
    public Result<Void> banUserByAccountId(Long accountId, String reason, Integer durationHours, Long operatorId) {
        return banUser(requireUserIdByAccountId(accountId), reason, durationHours, operatorId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> unbanUser(Long userId, Long operatorId) {
        Long operator = operatorId != null ? operatorId : userSupport.requireUserId();
        UserAccount account = refreshStatus(userId);
        if (account.getStatus() != UserAccountStatus.BANNED) {
            throw new BusinessException("账号未被封禁");
        }

        if (!clearBan(account)) {
            throw new BusinessException(ApiErrorCodes.CONFLICT, "账号状态已变化，请刷新后重试");
        }
        userSupport.logOperation(userId, OperationType.UNBAN,
                "operatorId=" + operator, null);
        return Result.success("解封成功");
    }

    @Override
    public Result<Void> unbanUserByAccountId(Long accountId, Long operatorId) {
        return unbanUser(requireUserIdByAccountId(accountId), operatorId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserAccount refreshStatus(Long userId) {
        return refreshStatus(getAccount(userId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserAccount refreshStatus(UserAccount account) {
        if (account == null) {
            throw new BusinessException("用户账号数据异常");
        }
        LocalDateTime now = LocalDateTime.now();

        if (account.getStatus() == UserAccountStatus.BANNED
                && account.getBanUntil() != null
                && account.getBanUntil().isBefore(now)) {
            clearBan(account);
            log.info("被动解封: userId={}", account.getUserId());
            return getAccount(account.getUserId());
        }

        if (account.getStatus() == UserAccountStatus.CANCELLING
                && account.getCancelAt() != null
                && account.getCancelAt().isBefore(now)) {
            completeCancellation(account);
            log.info("被动完成注销: userId={}", account.getUserId());
            return getAccount(account.getUserId());
        }

        return account;
    }

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

    @Override
    public void assertEditable(UserAccount account) {
        assertLoginAllowed(account);
        if (account.getStatus() == UserAccountStatus.CANCELLING) {
            throw new BusinessException(ApiErrorCodes.FORBIDDEN, "账号注销冷静期中，无法修改资料");
        }
    }

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

    private boolean completeCancellation(UserAccount account) {
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
        if (user != null) {
            userMapper.update(null, new LambdaUpdateWrapper<User>()
                    .eq(User::getId, user.getId())
                    .eq(User::getVersion, user.getVersion())
                    .set(User::getEmail, "cancelled_" + user.getId() + "@invalid.local")
                    .set(User::getVersion, user.getVersion() + 1)
                    .set(User::getDeleted, 1)
                    .set(User::getUpdateTime, LocalDateTime.now()));
        }

        sessionHelper.invalidateUserSession(account.getUserId());
        userSupport.logOperation(account.getUserId(), OperationType.CANCEL_COMPLETE, null, null);
        return true;
    }

    private UserAccount getAccount(Long userId) {
        UserAccount account = userAccountMapper.selectOne(new LambdaQueryWrapper<UserAccount>()
                .eq(UserAccount::getUserId, userId));
        if (account == null) {
            throw new BusinessException("用户账号数据异常");
        }
        return account;
    }

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

    private String requireBoundEmail(User user) {
        if (user == null || !StringUtils.hasText(user.getEmail())) {
            throw new BusinessException(ApiErrorCodes.BAD_REQUEST, "当前账号未绑定邮箱");
        }
        return EmailValidator.normalize(user.getEmail());
    }

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
