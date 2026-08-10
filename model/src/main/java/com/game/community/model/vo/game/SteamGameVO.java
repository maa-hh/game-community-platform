package com.game.community.model.vo.game;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class SteamGameVO implements Serializable {

    private Long appId;

    /** 对外展示名称，后端已按中文优先规则解析。 */
    private String name;

    private String nameZh;

    private String nameEn;

    private String iconUrl;

    private String coverUrl;

    private Integer playtimeForever;

    private Integer playtimeTwoWeeks;

    private Integer achievementUnlocked;

    private Integer achievementTotal;

    private LocalDateTime lastPlayedAt;

    private LocalDateTime syncedAt;
}
