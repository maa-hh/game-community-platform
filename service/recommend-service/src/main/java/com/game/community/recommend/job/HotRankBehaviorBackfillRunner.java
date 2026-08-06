package com.game.community.recommend.job;

import com.game.community.recommend.service.HotRankBehaviorBackfillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 启动时把历史社交行为同步到 t_article_behavior_event（表为空才执行）。
 */
@Slf4j
@Component
@Order(0)
@RequiredArgsConstructor
public class HotRankBehaviorBackfillRunner {

    private final HotRankBehaviorBackfillService hotRankBehaviorBackfillService;

    @Value("${recommend.startup.backfill-behavior-events:true}")
    private boolean backfillBehaviorEvents;

    @EventListener(ApplicationReadyEvent.class)
    public void backfillOnStartup() {
        if (!backfillBehaviorEvents) {
            log.info("历史行为回填已关闭（recommend.startup.backfill-behavior-events=false）");
            return;
        }
        try {
            long rows = hotRankBehaviorBackfillService.backfillFromSocial(false);
            if (rows > 0) {
                log.info("启动历史行为回填写入 {} 条", rows);
            }
        } catch (Exception e) {
            log.warn("启动历史行为回填失败，可手动触发 XXL-JOB hotRankBehaviorBackfillJob: {}", e.getMessage());
        }
    }
}
