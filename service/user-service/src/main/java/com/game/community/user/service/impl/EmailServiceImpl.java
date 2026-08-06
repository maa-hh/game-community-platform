package com.game.community.user.service.impl;

import com.game.community.common.constant.ApiErrorCodes;
import com.game.community.common.constant.user.UserConstants;
import com.game.community.common.exception.BusinessException;
import com.game.community.model.enums.user.CodeBizType;
import com.game.community.user.config.EmailAsyncProperties;
import com.game.community.user.service.EmailService;
import com.game.community.utils.config.EmailProperties;
import com.game.community.utils.email.EmailValidator;
import com.game.community.utils.email.SmtpMailClient;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * 邮箱验证码发送：线程池异步投递，等待结果；失败向上返回
 */
@Slf4j
@Service
public class EmailServiceImpl implements EmailService {

    private final EmailProperties emailProperties;
    private final EmailAsyncProperties emailAsyncProperties;
    private final SmtpMailClient smtpMailClient;
    private final Executor emailExecutor;

    private Set<String> mockEmailSet = Set.of();

    public EmailServiceImpl(EmailProperties emailProperties,
                            EmailAsyncProperties emailAsyncProperties,
                            SmtpMailClient smtpMailClient,
                            @Qualifier("emailExecutor") Executor emailExecutor) {
        this.emailProperties = emailProperties;
        this.emailAsyncProperties = emailAsyncProperties;
        this.smtpMailClient = smtpMailClient;
        this.emailExecutor = emailExecutor;
    }

    @PostConstruct
    public void init() {
        mockEmailSet = parseMockAddresses(emailProperties.getMock().getAddresses());
        log.info("邮箱 mock: enabled={}, addresses={}", emailProperties.getMock().isEnabled(), mockEmailSet);
    }

    @Override
    public void sendVerificationCode(String email, String code, CodeBizType bizType) {
        String normalized = EmailValidator.normalize(email);
        CodeBizType type = bizType == null ? CodeBizType.REGISTER : bizType;
        if (shouldMockSend(normalized)) {
            log.info("[邮箱模拟发送-{}] email={}, code={}", type, normalized, code);
            return;
        }

        try {
            long expireSeconds = resolveExpireSeconds();
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                String subject = buildSubject(type);
                String body = "您的验证码是：" + code + "，" + formatExpireHint(expireSeconds) + "。如非本人操作请忽略。";
                smtpMailClient.sendText(normalized, subject, body);
                log.info("[邮箱发送成功-{}] email={}", type, normalized);
            }, emailExecutor);

            future.get(emailAsyncProperties.getSendTimeoutSeconds(), TimeUnit.SECONDS);
        } catch (RejectedExecutionException e) {
            throw new BusinessException(ApiErrorCodes.TOO_MANY_REQUESTS, "邮件发送繁忙，请稍后重试");
        } catch (TimeoutException e) {
            throw new BusinessException(ApiErrorCodes.INTERNAL_ERROR, "邮件发送超时，请稍后重试");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof BusinessException businessException) {
                throw businessException;
            }
            log.warn("[邮箱发送失败-{}] email={}, error={}", type, normalized, cause.getMessage());
            throw new BusinessException(ApiErrorCodes.INTERNAL_ERROR, "邮件发送失败，请稍后重试");
        }
    }

    @Override
    public boolean useFixedCode(String email) {
        return shouldMockSend(EmailValidator.normalize(email))
                && StringUtils.hasText(emailProperties.getMock().getFixedCode());
    }

    @Override
    public String getMockFixedCode() {
        return emailProperties.getMock().getFixedCode();
    }

    private String buildSubject(CodeBizType bizType) {
        return switch (bizType) {
            case RESET_PASSWORD -> "游戏社区 - 找回密码验证码";
            case CHANGE_EMAIL_OLD, CHANGE_EMAIL_NEW -> "游戏社区 - 修改邮箱验证码";
            case CANCEL_ACCOUNT -> "游戏社区 - 注销账号验证码";
            case REGISTER -> "游戏社区 - 注册验证码";
        };
    }

    private long resolveExpireSeconds() {
        long configured = emailProperties.getCode().getExpireSeconds();
        return configured > 0 ? configured : UserConstants.CODE_EXPIRE;
    }

    /** 邮件文案中的有效期描述，与 Redis TTL 同源配置 */
    static String formatExpireHint(long expireSeconds) {
        long seconds = Math.max(1, expireSeconds);
        if (seconds % 60 == 0) {
            return (seconds / 60) + " 分钟内有效";
        }
        if (seconds > 60) {
            long minutes = seconds / 60;
            long remain = seconds % 60;
            return minutes + " 分 " + remain + " 秒内有效";
        }
        return seconds + " 秒内有效";
    }

    private boolean shouldMockSend(String email) {
        if (emailProperties.isForceReal()) {
            return false;
        }
        EmailProperties.Mock mock = emailProperties.getMock();
        if (!mock.isEnabled()) {
            return false;
        }
        if (mockEmailSet.contains("*")) {
            return true;
        }
        return mockEmailSet.contains(email);
    }

    private static Set<String> parseMockAddresses(String addresses) {
        if (!StringUtils.hasText(addresses)) {
            return Set.of();
        }
        return Arrays.stream(addresses.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(addr -> addr.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }
}
