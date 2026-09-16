package com.game.community.steam.runner;

import com.game.community.model.mongo.SteamGameAchievement;
import com.game.community.model.mongo.SteamGameDetail;
import com.game.community.steam.util.SteamAchievementIconUrl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** 启动时幂等迁移 Mongo 游戏富详情中的旧成就图标地址。 */
@Slf4j
@Component
@Order(100)
@RequiredArgsConstructor
public class SteamAchievementIconMigrationRunner implements ApplicationRunner {

    private static final String LEGACY_ICON_PATH = "/steamcommunity/public/images/apps/";

    private final MongoTemplate mongoTemplate;

    /** 启动幂等迁移，将 Mongo 中的历史成就图标地址转换为当前格式。 */
    @Override
    public void run(ApplicationArguments args) {
        Query query = Query.query(Criteria.where("achievementHighlights.iconUrl")
                .regex(LEGACY_ICON_PATH));
        int migrated = 0;
        for (SteamGameDetail detail : mongoTemplate.find(query, SteamGameDetail.class)) {
            boolean changed = false;
            if (detail.getAchievementHighlights() != null) {
                for (SteamGameAchievement achievement : detail.getAchievementHighlights()) {
                    if (achievement == null || achievement.getIconUrl() == null) {
                        continue;
                    }
                    String current = SteamAchievementIconUrl.toCurrent(achievement.getIconUrl());
                    if (!current.equals(achievement.getIconUrl())) {
                        achievement.setIconUrl(current);
                        changed = true;
                    }
                }
            }
            if (changed) {
                detail.setUpdateTime(LocalDateTime.now());
                mongoTemplate.save(detail);
                migrated++;
            }
        }
        if (migrated > 0) {
            log.info("Steam 成就图标地址迁移完成：MongoDB 更新 {} 条游戏详情", migrated);
        }
    }
}
