package com.game.community.steam.config;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Steam 定时任务统一由 XXL-JOB Admin 调度。 */
@Data
@Configuration
@ConfigurationProperties(prefix = "xxl.job")
public class XxlJobConfig {

    private String adminAddresses;
    private String accessToken;
    private String appname = "steam-service";
    private String address;
    private String ip;
    private int port = 9997;
    private String logPath = "logs/xxl-job/steam-service";
    private int logRetentionDays = 30;

    @Bean
    public XxlJobSpringExecutor xxlJobExecutor() {
        XxlJobSpringExecutor executor = new XxlJobSpringExecutor();
        executor.setAdminAddresses(adminAddresses);
        executor.setAppname(appname);
        executor.setAddress(address);
        executor.setIp(ip);
        executor.setPort(port);
        executor.setAccessToken(accessToken);
        executor.setLogPath(logPath);
        executor.setLogRetentionDays(logRetentionDays);
        return executor;
    }
}
