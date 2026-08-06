package com.game.community.utils.email;

import com.game.community.common.exception.MailSendException;
import com.game.community.utils.config.EmailProperties;
import jakarta.annotation.PostConstruct;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Properties;

/**
 * SMTP 邮件发送（第三方集成封装，服务层按需调用）
 */
@Slf4j
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(EmailProperties.class)
public class SmtpMailClient {

    private final EmailProperties emailProperties;
    private JavaMailSender mailSender;

    @PostConstruct
    public void init() {
        EmailProperties.Smtp smtp = emailProperties.getSmtp();
        if (!StringUtils.hasText(smtp.getHost())) {
            log.warn("SMTP 未配置（email.smtp.host），真实发信将不可用");
            return;
        }

        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(smtp.getHost());
        sender.setPort(smtp.getPort());
        sender.setUsername(smtp.getUsername());
        sender.setPassword(smtp.getPassword());
        sender.setDefaultEncoding("UTF-8");

        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", String.valueOf(StringUtils.hasText(smtp.getUsername())));
        props.put("mail.smtp.starttls.enable", String.valueOf(smtp.isStarttls()));
        mailSender = sender;
        log.info("SMTP 客户端初始化成功: host={}, from={}", smtp.getHost(), smtp.getFrom());
    }

    public boolean isReady() {
        return mailSender != null;
    }

    public void sendText(String to, String subject, String text) {
        if (!isReady()) {
            throw new MailSendException(500, "邮件服务未配置，请联系管理员");
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(emailProperties.getSmtp().getFrom());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(text, false);
            mailSender.send(message);
            log.info("[SMTP 发送成功] to={}, subject={}", to, subject);
        } catch (Exception e) {
            log.error("[SMTP 发送失败] to={}, error={}", to, e.getMessage(), e);
            throw toBusinessException(e);
        }
    }

    private static MailSendException toBusinessException(Exception e) {
        String raw = e.getMessage() == null ? "" : e.getMessage();
        String lower = raw.toLowerCase();

        if (lower.contains("550") || lower.contains("non-existent account")
                || (lower.contains("recipient") && lower.contains("invalid"))) {
            return new MailSendException(400, "收件邮箱不存在或无法接收邮件，请检查邮箱地址");
        }
        if (lower.contains("535") || lower.contains("authentication")
                || (lower.contains("auth") && lower.contains("fail"))) {
            return new MailSendException(500, "邮件服务认证失败，请检查 SMTP 配置");
        }
        if (lower.contains("connection") || lower.contains("connect timed out")
                || lower.contains("could not connect")) {
            return new MailSendException(500, "邮件服务连接失败，请稍后重试");
        }
        return new MailSendException(500, "邮件发送失败，请稍后重试");
    }
}
