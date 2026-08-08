package com.game.community.user.event.executor;

import com.game.community.common.constant.user.UserConstants;
import com.game.community.common.constant.email.EmailConstants;
import com.game.community.model.enums.user.CodeBizType;
import com.game.community.utils.email.SmtpMailClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executor;

/** 邮件任务：提交到邮件线程池并执行 SMTP 发送，不负责重试。 */
@Slf4j
@Component
public class EmailTaskExecutor {

    private final Executor emailExecutor;
    private final SmtpMailClient smtpMailClient;

    public EmailTaskExecutor(@Qualifier("emailExecutor") Executor emailExecutor,
                             SmtpMailClient smtpMailClient) {
        this.emailExecutor = emailExecutor;
        this.smtpMailClient = smtpMailClient;
    }

    /** 发送参数已由验证码入口校验；此处只负责在线程池中执行 SMTP。 */
    public void submit(String email, String code, CodeBizType type, long expireSeconds) {
        emailExecutor.execute(() -> {
            try {
                String subject = switch (type) {
                    case RESET_PASSWORD -> EmailConstants.SUBJECT_RESET_PASSWORD;
                    case CHANGE_EMAIL_OLD, CHANGE_EMAIL_NEW -> EmailConstants.SUBJECT_CHANGE_EMAIL;
                    case CANCEL_ACCOUNT -> EmailConstants.SUBJECT_CANCEL_ACCOUNT;
                    case REGISTER -> EmailConstants.SUBJECT_REGISTER;
                };
                String body = EmailConstants.BODY_PREFIX + code + EmailConstants.BODY_CODE_SEPARATOR
                        + formatExpireHint(expireSeconds) + EmailConstants.BODY_SUFFIX;
                smtpMailClient.sendText(email, subject, body);
                log.info("[邮箱发送成功-{}] email={}", type, email);
            } catch (Exception e) {
                log.error("[邮箱发送失败-不重试-告警] email={}, type={}", email, type, e);
            }
        });
    }

    private String formatExpireHint(long expireSeconds) {
        long seconds = Math.max(UserConstants.MIN_POSITIVE_SECONDS, expireSeconds);
        if (seconds % UserConstants.SECONDS_PER_MINUTE == 0) {
            return seconds / UserConstants.SECONDS_PER_MINUTE + EmailConstants.EXPIRE_MINUTE_SUFFIX;
        }
        if (seconds > UserConstants.SECONDS_PER_MINUTE) {
            return seconds / UserConstants.SECONDS_PER_MINUTE
                    + EmailConstants.EXPIRE_MINUTE_SECOND_SEPARATOR
                    + seconds % UserConstants.SECONDS_PER_MINUTE + EmailConstants.EXPIRE_SECOND_SUFFIX;
        }
        return seconds + EmailConstants.EXPIRE_SECOND_SUFFIX;
    }
}
