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
import java.util.List;
import java.util.Locale;
import java.util.Random;

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
    private final Random random = new Random();

    public int sendEmailCode(String email, CodeBizType bizType) {
        String normalized = normalize(email);
        CodeBizType type = normalizeBizType(bizType);
        String typeCode = type.getCode();
        assertNotVerifyLocked(normalized);

        String cooldownKey = RedisConstants.SEND_CODE_COOLDOWN_PREFIX + typeCode + ":" + normalized;
        String dailyKey = RedisConstants.SEND_CODE_DAILY_PREFIX + typeCode + ":" + normalized;
        List<String> sendGuardValues = redisUtils.multiGet(cooldownKey, dailyKey);
        String cooldownValue = sendGuardValues.get(0);
        if (cooldownValue != null) {
            long waitSec = Math.max(1, redisUtils.getExpireSeconds(cooldownKey));
            throw new BusinessException(ApiErrorCodes.TOO_MANY_REQUESTS, "发送过于频繁，请 " + waitSec + " 秒后再试");
        }

        int dailyCount = parseInt(sendGuardValues.get(1));
        if (dailyCount >= UserConstants.SEND_CODE_DAILY_LIMIT) {
            throw new BusinessException(ApiErrorCodes.TOO_MANY_REQUESTS, "今日发送次数已达上限，请明天再试");
        }

        long expireSeconds = resolveExpireSeconds();
        String code = emailService.useFixedCode(normalized)
                ? emailService.getMockFixedCode()
                : randomCode();
        redisUtils.del(RedisConstants.CODE_VERIFY_FAIL_PREFIX + normalized);
        redisUtils.setEx(RedisConstants.codeKey(typeCode, normalized), code, expireSeconds);
        redisUtils.setEx(cooldownKey, "1", UserConstants.SEND_CODE_COOLDOWN);

        long secondsUntilMidnight = Duration.between(
                LocalDateTime.now(),
                LocalDate.now().plusDays(1).atTime(LocalTime.MIDNIGHT)
        ).getSeconds();
        redisUtils.setEx(dailyKey, String.valueOf(dailyCount + 1), secondsUntilMidnight);

        emailService.sendVerificationCode(normalized, code, type);
        return (int) expireSeconds;
    }

    public long resolveExpireSeconds() {
        long configured = emailProperties.getCode().getExpireSeconds();
        return configured > 0 ? configured : UserConstants.CODE_EXPIRE;
    }

    public void verify(String email, CodeBizType bizType, String inputCode) {
        assertCodeMatches(email, bizType, inputCode, true);
    }

    /** 校验验证码但不删除（改邮箱中间步骤需要复用原邮箱验证码） */
    public void assertCodeMatches(String email, CodeBizType bizType, String inputCode) {
        assertCodeMatches(email, bizType, inputCode, false);
    }

    private void assertCodeMatches(String email, CodeBizType bizType, String inputCode, boolean consume) {
        String normalized = normalize(email);
        CodeBizType type = normalizeBizType(bizType);
        String typeCode = type.getCode();
        assertNotVerifyLocked(normalized);

        String codeKey = RedisConstants.codeKey(typeCode, normalized);
        String storedCode = redisUtils.get(codeKey);
        if (storedCode == null) {
            throw new BusinessException(ApiErrorCodes.BAD_REQUEST, "验证码已过期，请重新获取");
        }
        if (!storedCode.equals(inputCode)) {
            handleVerifyFailure(normalized);
            throw new BusinessException(ApiErrorCodes.BAD_REQUEST, "验证码错误");
        }

        redisUtils.del(RedisConstants.CODE_VERIFY_FAIL_PREFIX + normalized);
        if (consume) {
            redisUtils.del(codeKey);
        }
    }

    public String randomCode() {
        return String.format("%06d", random.nextInt(1_000_000));
    }

    private void assertNotVerifyLocked(String email) {
        if (redisUtils.get(RedisConstants.CODE_VERIFY_LOCK_PREFIX + email) != null) {
            throw new BusinessException(ApiErrorCodes.FORBIDDEN, "验证码验证失败次数过多，请"
                    + UserConstants.CODE_VERIFY_LOCK_DURATION_MINUTES + "分钟后重试");
        }
    }

    private void handleVerifyFailure(String email) {
        String failKey = RedisConstants.CODE_VERIFY_FAIL_PREFIX + email;
        int failCount = parseInt(redisUtils.get(failKey)) + 1;

        log.warn("[验证码验证失败] email={}, failCount={}/{}", email, failCount,
                UserConstants.CODE_VERIFY_FAIL_THRESHOLD);

        if (failCount >= UserConstants.CODE_VERIFY_FAIL_THRESHOLD) {
            long lockSeconds = UserConstants.CODE_VERIFY_LOCK_DURATION_MINUTES * 60L;
            redisUtils.setEx(RedisConstants.CODE_VERIFY_LOCK_PREFIX + email, "1", lockSeconds);
            redisUtils.del(failKey);
            return;
        }
        redisUtils.setEx(failKey, String.valueOf(failCount), UserConstants.CODE_VERIFY_FAIL_EXPIRE);
    }

    public static CodeBizType normalizeBizType(CodeBizType bizType) {
        return bizType == null ? CodeBizType.REGISTER : bizType;
    }

    private static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private static int parseInt(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        return Integer.parseInt(value);
    }
}
