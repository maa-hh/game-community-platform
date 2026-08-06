package com.game.community.model.vo.game;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class SteamUserAchievementVO implements Serializable {

    private String apiName;

    private String name;

    private String description;

    private String iconUrl;

    private Boolean unlocked;

    private LocalDateTime unlockTime;

    /** 全球玩家解锁率 0–100 */
    private Double globalPercent;
}
