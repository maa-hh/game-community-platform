package com.game.community.search.config;

import com.game.community.search.initIndex.InitElasticsearchIndex;
import com.game.community.search.service.SearchStartupSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SearchStartupInitializer {

    private final InitElasticsearchIndex initElasticsearchIndex;
    private final SearchStartupSyncService searchStartupSyncService;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("应用就绪，开始初始化搜索索引");
        initElasticsearchIndex.initIndex();
        log.info("搜索索引初始化完成，触发启动数据同步");
        searchStartupSyncService.syncOnStartupIfEnabled();
    }
}
