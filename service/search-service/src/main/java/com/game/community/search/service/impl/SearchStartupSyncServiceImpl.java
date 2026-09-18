package com.game.community.search.service.impl;

import com.game.community.common.constant.search.SearchConstants;
import com.game.community.search.config.SearchSyncProperties;
import com.game.community.search.service.ArticleSyncService;
import com.game.community.search.service.GameSearchService;
import com.game.community.search.service.SearchStartupSyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

@Slf4j
@Service
public class SearchStartupSyncServiceImpl implements SearchStartupSyncService {

    private final SearchSyncProperties searchSyncProperties;
    private final ArticleSyncService articleSyncService;
    private final GameSearchService gameSearchService;
    private final DataSource dataSource;
    private final Executor searchSyncExecutor;

    public SearchStartupSyncServiceImpl(SearchSyncProperties searchSyncProperties,
                                        ArticleSyncService articleSyncService,
                                        GameSearchService gameSearchService,
                                        DataSource dataSource,
                                        @Qualifier("searchSyncExecutor") Executor searchSyncExecutor) {
        this.searchSyncProperties = searchSyncProperties;
        this.articleSyncService = articleSyncService;
        this.gameSearchService = gameSearchService;
        this.dataSource = dataSource;
        this.searchSyncExecutor = searchSyncExecutor;
    }

    @Override
    public boolean syncOnStartupIfEnabled() {
        if (!searchSyncProperties.isOnStartup()) {
            log.info("启动数据同步已关闭(search.sync.on-startup=false)");
            return true;
        }
        return rebuildInternal();
    }

    @Override
    public boolean rebuildAsync() {
        try {
            searchSyncExecutor.execute(this::rebuildNow);
            return true;
        } catch (RejectedExecutionException e) {
            log.info("搜索索引重建任务已在执行，拒绝重复提交");
            return false;
        }
    }

    @Override
    public void rebuildNow() {
        rebuildInternal();
    }

    private boolean rebuildInternal() {
        long startedAt = System.currentTimeMillis();
        try (Connection connection = dataSource.getConnection()) {
            if (!tryAcquireLock(connection)) {
                log.info("已有其他实例执行搜索索引启动重建，本实例跳过");
                return true;
            }
            log.info("开始启动数据同步（文章索引 + 建议词）");
            try {
                articleSyncService.rebuildAll();
                gameSearchService.rebuildFromSteam();
                log.info("启动数据同步完成, costMs={}", System.currentTimeMillis() - startedAt);
                return true;
            } catch (Exception e) {
                log.error("启动数据同步失败, costMs={}, reason={}",
                        System.currentTimeMillis() - startedAt, e.getMessage(), e);
                return false;
            } finally {
                releaseLock(connection);
            }
        } catch (Exception e) {
            log.error("获取搜索索引启动锁失败, reason={}", e.getMessage(), e);
            return false;
        }
    }

    private boolean tryAcquireLock(Connection connection) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("SELECT GET_LOCK(?, ?)")) {
            statement.setString(1, SearchConstants.STARTUP_REBUILD_LOCK);
            statement.setInt(2, SearchConstants.STARTUP_REBUILD_LOCK_TIMEOUT_SECONDS);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getInt(1) == 1;
            }
        }
    }

    private void releaseLock(Connection connection) {
        try (PreparedStatement statement = connection.prepareStatement("SELECT RELEASE_LOCK(?)")) {
            statement.setString(1, SearchConstants.STARTUP_REBUILD_LOCK);
            statement.execute();
        } catch (Exception e) {
            log.warn("释放搜索索引启动锁失败: {}", e.getMessage());
        }
    }
}
