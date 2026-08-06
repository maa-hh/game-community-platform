package com.game.community.content.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * OG 分享落地页配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "share")
public class SharePageProperties {

    /**
     * 前端站点根地址，用于跳转到 /post/{id}
     */
    private String frontendBaseUrl = "http://localhost:3000";

    /**
     * 分享落地页公网根地址（网关），用于 og:url，如 http://localhost:8080
     */
    private String shareBaseUrl = "http://localhost:8080";

    /**
     * 站点名称（og:site_name）
     */
    private String siteName = "游戏社区";

    /**
     * 无封面时的默认 OG 图（须为绝对 URL）
     */
    private String defaultImageUrl = "";
}
