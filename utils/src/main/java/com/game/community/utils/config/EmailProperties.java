package com.game.community.utils.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 邮箱 SMTP / mock / 校验配置（yaml: email.*）
 */
@Data
@ConfigurationProperties(prefix = "email")
public class EmailProperties {

    private Validation validation = new Validation();

    private Mock mock = new Mock();

    private boolean forceReal = false;

    private Smtp smtp = new Smtp();

    private Code code = new Code();

    @Data
    public static class Validation {
        /** 是否校验 DNS MX 记录 */
        private boolean mxCheckEnabled = true;
    }

    @Data
    public static class Code {
        /** 验证码有效期（秒），Redis TTL 与邮件文案共用 */
        private long expireSeconds = 300;
    }

    @Data
    public static class Mock {
        private boolean enabled = false;
        private String addresses = "";
        private String fixedCode = "123456";
    }

    @Data
    public static class Smtp {
        private String host = "";
        private int port = 587;
        private String username = "";
        private String password = "";
        private String from = "noreply@game.com";
        private boolean starttls = true;
    }
}
