package com.game.community.user.service;

import com.game.community.common.exception.MailSendException;
import com.game.community.model.enums.user.CodeBizType;
import com.game.community.user.event.executor.EmailTaskExecutor;
import com.game.community.user.service.impl.EmailServiceImpl;
import com.game.community.utils.config.EmailProperties;
import com.game.community.utils.email.SmtpMailClient;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class EmailServiceImplTest {

    private final SmtpMailClient smtpMailClient = mock(SmtpMailClient.class);

    @Test
    void shouldSubmitEmailWithoutWaitingForSmtp() {
        AtomicReference<Runnable> submittedTask = new AtomicReference<>();
        EmailServiceImpl service = newService(submittedTask::set);

        service.sendVerificationCode("player@game.com", "123456", CodeBizType.REGISTER);

        assertThat(submittedTask.get()).isNotNull();
        verifyNoInteractions(smtpMailClient);

        submittedTask.get().run();
        verify(smtpMailClient).sendText(
                "player@game.com", "游戏社区 - 注册验证码",
                "您的验证码是：123456，5 分钟内有效。如非本人操作请忽略。");
    }

    @Test
    void shouldSkipSendingForMockEmail() {
        EmailProperties properties = new EmailProperties();
        properties.getMock().setEnabled(true);
        properties.getMock().setAddresses("*");
        EmailServiceImpl service = new EmailServiceImpl(properties,
                new EmailTaskExecutor(Runnable::run, smtpMailClient));
        service.init();

        service.sendVerificationCode("player@game.com", "123456", CodeBizType.REGISTER);

        verifyNoInteractions(smtpMailClient);
    }

    @Test
    void shouldLogFailureWithoutRetrying() {
        doThrow(new MailSendException(500, "SMTP 暂时不可用"))
                .when(smtpMailClient).sendText(any(), any(), any());
        EmailServiceImpl service = newService(Runnable::run);

        service.sendVerificationCode("player@game.com", "123456", CodeBizType.REGISTER);

        verify(smtpMailClient).sendText(
                "player@game.com", "游戏社区 - 注册验证码",
                "您的验证码是：123456，5 分钟内有效。如非本人操作请忽略。");
    }

    private EmailServiceImpl newService(Executor executor) {
        EmailProperties properties = new EmailProperties();
        properties.getCode().setExpireSeconds(300);
        EmailServiceImpl service = new EmailServiceImpl(properties,
                new EmailTaskExecutor(executor, smtpMailClient));
        service.init();
        return service;
    }
}
