package com.game.community.search.config;

import com.game.community.search.initIndex.InitElasticsearchIndex;
import com.game.community.search.service.ArticleSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class IndexInitRunner implements CommandLineRunner {

    private final InitElasticsearchIndex initElasticsearchIndex;
    private final ArticleSyncService articleSyncService;

    @Override
    public void run(String... args) {
        log.info("开始初始化搜索索引");
        initElasticsearchIndex.initIndex();
        log.info("搜索索引初始化完成");
        try {
            log.info("开始重建已发布文章搜索索引");
            articleSyncService.rebuildPublishedArticles();
            log.info("已发布文章搜索索引重建完成");
            log.info("开始重建搜索建议词索引");
            articleSyncService.rebuildArticleSuggestions();
            log.info("搜索建议词索引重建完成");
        } catch (Exception e) {
            log.warn("已发布文章搜索索引重建失败，将等待 Kafka 增量同步或手动重建: {}", e.getMessage(), e);
        }
    }
}
