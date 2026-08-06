package com.game.community.search.job;

import com.game.community.search.service.SuggestTermService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SuggestTermCleanupJob {

    private final SuggestTermService suggestTermService;

    /** 每天凌晨 3 点清理冷词 */
    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanupColdTerms() {
        int expired = suggestTermService.cleanupExpiredTerms();
        log.info("定时清理建议词完成: expired={}", expired);
    }
}
