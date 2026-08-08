package com.game.community.user.service.impl;

import com.game.community.common.constant.ApiErrorCodes;
import com.game.community.common.constant.user.UserConstants;
import com.game.community.common.exception.BusinessException;
import com.game.community.model.enums.user.CodeBizType;
import com.game.community.user.event.executor.EmailTaskExecutor;
import com.game.community.user.service.EmailService;
import com.game.community.utils.config.EmailProperties;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.RejectedExecutionException;
import java.util.stream.Collectors;

/** 邮箱验证码发送：提交到线程池后立即返回，失败只记录日志，不自动重试。 */
@Slf4j
@Service
public class EmailServiceImpl implements EmailService {

    private final EmailProperties emailProperties;
    private final EmailTaskExecutor emailTaskExecutor;

    private Set<String> mockEmailSet = Set.of();

    /** 执行 EmailServiceImpl 对应的业务处理。 */
    public EmailServiceImpl(EmailProperties emailProperties, EmailTaskExecutor emailTaskExecutor) {
        this.emailProperties = emailProperties;
        this.emailTaskExecutor = emailTaskExecutor;
    }

    /** 执行 init 对应的业务处理。 */
    @PostConstruct
    public void init() {
        mockEmailSet = parseMockAddresses(emailProperties.getMock().getAddresses());
        log.info("邮箱 mock: enabled={}, addresses={}", emailProperties.getMock().isEnabled(), mockEmailSet);
    }

    /** 执行 sendVerificationCode 对应的业务处理。 */
    @Override
    public void sendVerificationCode(String email, String code, CodeBizType bizType) {
        CodeBizType type = bizType == null ? CodeBizType.REGISTER : bizType;
        if (shouldMockSend(email)) {
            log.info("[邮箱模拟发送-{}] email={}, code={}", type, email, code);
            return;
        }

        long configuredExpireSeconds = emailProperties.getCode().getExpireSeconds();
        long expireSeconds = configuredExpireSeconds > 0
                ? configuredExpireSeconds : UserConstants.CODE_EXPIRE;
        try {
            emailTaskExecutor.submit(email, code, type, expireSeconds);
        } catch (RejectedExecutionException e) {
            throw new BusinessException(ApiErrorCodes.TOO_MANY_REQUESTS, "邮件发送繁忙，请稍后重试");
        }
    }

    /** 执行 useFixedCode 对应的业务处理。 */
    @Override
    public boolean useFixedCode(String email) {
        return shouldMockSend(email)
                && StringUtils.hasText(emailProperties.getMock().getFixedCode());
    }

    /** 执行 getMockFixedCode 对应的业务处理。 */
    @Override
    public String getMockFixedCode() {
        return emailProperties.getMock().getFixedCode();
    }

    /** 执行 shouldMockSend 对应的业务处理。 */
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

    /** 执行 parseMockAddresses 对应的业务处理。 */
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
