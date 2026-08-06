package com.game.community.audit.job;

import com.game.community.audit.service.ModerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ModerationAutoPassJob {

    private final ModerationService moderationService;

    @Scheduled(cron = "0 15 4 * * ?")
    public void autoPassExpiredTasks() {
        int count = moderationService.autoPassExpiredTasks();
        if (count > 0) {
            log.info("人工审核超时自动通过完成: count={}", count);
        }
    }

    @Scheduled(fixedDelayString = "${audit.moderation.recovery-interval-ms:60000}")
    public void recoverExpiredClaims() {
        int count = moderationService.recoverExpiredClaims();
        if (count > 0) {
            log.warn("审核认领租约过期，任务重新入队: count={}", count);
        }
    }
}
