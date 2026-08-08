package com.game.community.user.common.verification;

import com.game.community.common.constant.ApiErrorCodes;
import com.game.community.common.constant.user.RedisConstants;
import com.game.community.common.constant.user.UserConstants;
import com.game.community.common.exception.BusinessException;
import com.game.community.model.enums.user.CodeBizType;
import com.game.community.user.service.EmailService;
import com.game.community.utils.RedisUtils;
import com.game.community.utils.config.EmailProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.security.SecureRandom;

/**
 * 邮箱验证码发送/校验
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VerificationCodeHelper {

    private final RedisUtils redisUtils;
    private final EmailProperties emailProperties;
    private final EmailService emailService;
    private final SecureRandom random = new SecureRandom();

    /** 邮箱已由业务入口规范化；这里负责限流、落 Redis 验证码并提交发送任务。 */
    public int sendEmailCode(String email, CodeBizType bizType) {
        // 验证码先原子占用冷却和每日额度，再提交邮件；额度失败不会发送邮件。
        String normalized = email;
        CodeBizType type = bizType == null ? CodeBizType.REGISTER : bizType;
        String typeCode = type.getCode();
        assertNotVerifyLocked(normalized);

        String cooldownKey = RedisConstants.SEND_CODE_COOLDOWN_PREFIX + typeCode + ":" + normalized;
        String dailyKey = RedisConstants.SEND_CODE_DAILY_PREFIX + typeCode + ":" + normalized;
        long configuredExpireSeconds = emailProperties.getCode().getExpireSeconds();
        long expireSeconds = configuredExpireSeconds > 0
                ? configuredExpireSeconds : UserConstants.CODE_EXPIRE;
        String code = emailService.useFixedCode(normalized)
                ? emailService.getMockFixedCode()
                : String.format("%0" + UserConstants.CODE_LENGTH + "d",
                random.nextInt(UserConstants.CODE_RANDOM_BOUND));
        long secondsUntilMidnight = Duration.between(
                LocalDateTime.now(),
                LocalDate.now().plusDays(1).atTime(LocalTime.MIDNIGHT)
        ).getSeconds();
        long reserved = redisUtils.reserveVerificationCode(
                cooldownKey,
                dailyKey,
                RedisConstants.codeKey(typeCode, normalized),
                RedisConstants.CODE_VERIFY_FAIL_PREFIX + normalized,
                code,
                expireSeconds,
                UserConstants.SEND_CODE_COOLDOWN,
                Math.max(UserConstants.MIN_POSITIVE_SECONDS, secondsUntilMidnight),
                UserConstants.SEND_CODE_DAILY_LIMIT);
        if (reserved == RedisConstants.RESERVE_COOLDOWN) {
            long waitSec = Math.max(UserConstants.MIN_POSITIVE_SECONDS,
                    redisUtils.getExpireSeconds(cooldownKey));
            throw new BusinessException(ApiErrorCodes.TOO_MANY_REQUESTS, "发送过于频繁，请 " + waitSec + " 秒后再试");
        }
        if (reserved == RedisConstants.RESERVE_DAILY_LIMIT) {
            throw new BusinessException(ApiErrorCodes.TOO_MANY_REQUESTS, "今日发送次数已达上限，请明天再试");
        }
        if (reserved == RedisConstants.RESERVE_UNAVAILABLE) {
            throw new BusinessException(ApiErrorCodes.INTERNAL_ERROR, "验证码服务暂不可用");
        }

        emailService.sendVerificationCode(normalized, code, type);
        // 返回 TTL 给前端倒计时，实际 SMTP 发送由 EmailTaskExecutor 异步完成。
        return (int) expireSeconds;
    }

    /** 执行 verify 对应的业务处理。 */
    public void verify(String email, CodeBizType bizType, String inputCode) {
        assertCodeMatches(email, bizType, inputCode, true);
    }

    /** 校验验证码但不删除（改邮箱中间步骤需要复用原邮箱验证码） */
    public void assertCodeMatches(String email, CodeBizType bizType, String inputCode) {
        assertCodeMatches(email, bizType, inputCode, false);
    }

    /** 执行 assertCodeMatches 对应的业务处理。 */
    private void assertCodeMatches(String email, CodeBizType bizType, String inputCode, boolean consume) {
        String normalized = email;
        CodeBizType type = bizType == null ? CodeBizType.REGISTER : bizType;
        String typeCode = type.getCode();
        assertNotVerifyLocked(normalized);

        String codeKey = RedisConstants.codeKey(typeCode, normalized);
        String storedCode = redisUtils.get(codeKey);
        if (storedCode == null) {
            throw new BusinessException(ApiErrorCodes.BAD_REQUEST, "验证码已过期，请重新获取");
        }
        if (!storedCode.equals(inputCode)) {
            // 错误次数写 Redis 并在达到阈值后锁定，防止验证码被暴力试探。
            handleVerifyFailure(normalized);
            throw new BusinessException(ApiErrorCodes.BAD_REQUEST, "验证码错误");
        }

        redisUtils.del(RedisConstants.CODE_VERIFY_FAIL_PREFIX + normalized);
        if (consume) {
            redisUtils.del(codeKey);
        }
    }

    /** 执行 assertNotVerifyLocked 对应的业务处理。 */
    private void assertNotVerifyLocked(String email) {
        if (redisUtils.get(RedisConstants.CODE_VERIFY_LOCK_PREFIX + email) != null) {
            throw new BusinessException(ApiErrorCodes.FORBIDDEN, "验证码验证失败次数过多，请"
                    + UserConstants.CODE_VERIFY_LOCK_DURATION_MINUTES + "分钟后重试");
        }
    }

    /** 执行 handleVerifyFailure 对应的业务处理。 */
    private void handleVerifyFailure(String email) {
        String failKey = RedisConstants.CODE_VERIFY_FAIL_PREFIX + email;
        long failCount = redisUtils.recordVerificationFailure(
                failKey,
                RedisConstants.CODE_VERIFY_LOCK_PREFIX + email,
                UserConstants.CODE_VERIFY_FAIL_EXPIRE,
                UserConstants.CODE_VERIFY_FAIL_THRESHOLD,
                UserConstants.CODE_VERIFY_LOCK_DURATION_MINUTES * 60L);

        log.warn("[验证码验证失败] email={}, failCount={}/{}", email, failCount,
                UserConstants.CODE_VERIFY_FAIL_THRESHOLD);

        if (failCount == RedisConstants.RESERVE_UNAVAILABLE) {
            throw new BusinessException(ApiErrorCodes.INTERNAL_ERROR, "验证码服务暂不可用");
        }
    }

}
