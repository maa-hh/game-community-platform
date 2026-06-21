package com.game.community.recommend.job;

import com.game.community.recommend.service.HotArticleService;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class HotArticleJobHandler {

    private final HotArticleService hotArticleService;

    @XxlJob("hotArticleCalculateJobHandler")
    public void hotArticleCalculateJobHandler() {
        log.info("XXL-JOB 触发每日热榜重建");
        hotArticleService.calculateHotArticles();
    }
}
