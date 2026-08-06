package com.game.community.recommend.job;

import com.game.community.recommend.service.HotRankBehaviorBackfillService;
import com.game.community.recommend.service.HotRankService;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class HotArticleJobHandler {

    private final HotRankService hotRankService;
    private final HotRankBehaviorBackfillService hotRankBehaviorBackfillService;

    @XxlJob("hotRankTotalDailyJob")
    public void hotRankTotalDailyJob() {
        log.info("XXL-JOB 触发总榜日更");
        hotRankService.rebuildTotalBoard();
    }

    @XxlJob("hotRankWeeklyJob")
    public void hotRankWeeklyJob() {
        log.info("XXL-JOB 触发周榜固化");
        hotRankService.finalizeWeeklyBoard();
    }

    @XxlJob("hotRankDailyFinalizeJob")
    public void hotRankDailyFinalizeJob() {
        log.info("XXL-JOB 触发日榜终榜固化");
        hotRankService.finalizeDailyBoard(null);
    }

    @XxlJob("hotRankBehaviorBackfillJob")
    public void hotRankBehaviorBackfillJob() {
        log.info("XXL-JOB 触发历史行为事件回填");
        long rows = hotRankBehaviorBackfillService.backfillFromSocial(true);
        log.info("历史行为回填完成，写入 {} 条，开始重建总榜", rows);
        hotRankService.rebuildTotalBoard();
    }
}
