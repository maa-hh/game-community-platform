package com.game.community.steam.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "steam")
public class SteamProperties {

    private String webApiKey = "";

    private String openidRealm = "http://localhost:8080";

    private String openidReturnTo = "http://localhost:8080/steam/callback";

    private String apiLang = "schinese";

    private String apiCc = "cn";

    private String frontendRedirect = "http://localhost:3000/profile";

    /** 榜单定时同步 cron 表达式。 */
    private String chartSyncCron = "0 0 5 * * ?";

    /** Steam 出站请求之间的最小间隔，避免多个异步线程形成请求洪峰。 */
    private long requestIntervalMs = 600L;

    /** Steam 返回 429 时的最大 GET/HEAD 请求次数。 */
    private int rateLimitMaxAttempts = 3;

    /** Steam 返回 429 后的首次退避时长，后续按尝试次数递增。 */
    private long rateLimitRetryDelayMs = 3000L;
}
