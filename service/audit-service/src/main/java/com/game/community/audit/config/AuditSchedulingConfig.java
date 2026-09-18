package com.game.community.audit.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 开启审核服务的租约回收与 Outbox 发布调度。 */
@Configuration
@EnableScheduling
public class AuditSchedulingConfig {
}
