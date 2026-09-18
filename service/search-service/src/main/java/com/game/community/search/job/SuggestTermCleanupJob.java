package com.game.community.search.job;

import com.game.community.common.constant.search.SearchConstants;
import com.game.community.search.config.SearchMaintenanceProperties;
import com.game.community.search.service.SuggestTermService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

@Slf4j
@Component
@RequiredArgsConstructor
public class SuggestTermCleanupJob {

    private final SuggestTermService suggestTermService;
    private final DataSource dataSource;
    private final SearchMaintenanceProperties maintenanceProperties;

    /** 多实例部署下由 MySQL named lock 选出一个实例执行冷词清理。 */
    @Scheduled(cron = "${search.maintenance.suggest-cleanup-cron:0 0 3 * * ?}")
    public void cleanupColdTerms() {
        try (Connection connection = dataSource.getConnection()) {
            if (!tryAcquireLock(connection)) {
                log.debug("已有其他实例执行建议词清理，本实例跳过");
                return;
            }
            try {
                int expired = suggestTermService.cleanupExpiredTerms();
                log.info("定时清理建议词完成: expired={}", expired);
            } finally {
                releaseLock(connection);
            }
        } catch (Exception e) {
            log.error("定时清理建议词失败", e);
        }
    }

    private boolean tryAcquireLock(Connection connection) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("SELECT GET_LOCK(?, ?)")) {
            statement.setString(1, SearchConstants.SUGGEST_CLEANUP_LOCK);
            statement.setInt(2, maintenanceProperties.getLockTimeoutSeconds());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getInt(1) == 1;
            }
        }
    }

    private void releaseLock(Connection connection) {
        try (PreparedStatement statement = connection.prepareStatement("SELECT RELEASE_LOCK(?)")) {
            statement.setString(1, SearchConstants.SUGGEST_CLEANUP_LOCK);
            statement.execute();
        } catch (Exception e) {
            log.warn("释放建议词清理锁失败: {}", e.getMessage());
        }
    }
}
