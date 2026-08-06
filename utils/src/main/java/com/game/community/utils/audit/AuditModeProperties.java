package com.game.community.utils.audit;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 审核模式与阈值配置。
 */
@Data
@ConfigurationProperties(prefix = "audit")
public class AuditModeProperties {

    /**
     * mock | llm
     */
    private String mode = "mock";

    public boolean isLlmMode() {
        return "llm".equalsIgnoreCase(mode);
    }
}
