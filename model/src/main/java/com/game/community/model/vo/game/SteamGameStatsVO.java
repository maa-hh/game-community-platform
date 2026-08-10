package com.game.community.model.vo.game;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class SteamGameStatsVO implements Serializable {

    private Long appId;

    private String name;

    private String nameZh;

    private String nameEn;

    private Boolean owned;

    private Integer playtimeForever;

    private Integer playtimeTwoWeeks;

    private LocalDateTime lastPlayedAt;

    private Integer achievementUnlocked;

    private Integer achievementTotal;

    private List<SteamUserAchievementVO> achievements;

    /** 成就定义/玩家状态的缓存状态：READY、LOADING、SYNCING、NOT_AVAILABLE。 */
    private String achievementStatus;

    private LocalDateTime achievementSyncedAt;

    private LocalDateTime achievementNextRefreshAt;
}
