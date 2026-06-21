package com.game.community.recommend.job;

import com.game.community.recommend.service.HotArticleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class HotArticleWarmupRunner {

    private final HotArticleService hotArticleService;

    @EventListener(ApplicationReadyEvent.class)
    public void warmupHotRank() {
        try {
            hotArticleService.calculateHotArticles();
        } catch (Exception e) {
            log.warn("启动预热文章热榜失败，将在访问或 XXL-JOB 调度时重试: {}", e.getMessage());
        }
    }
}
