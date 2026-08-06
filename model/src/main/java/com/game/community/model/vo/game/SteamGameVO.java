package com.game.community.model.vo.game;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class SteamGameVO implements Serializable {

    private Long appId;

    private String name;

    private String iconUrl;

    private Integer playtimeForever;

    private Integer playtimeTwoWeeks;

    private Integer achievementUnlocked;

    private Integer achievementTotal;

    private LocalDateTime lastPlayedAt;

    private LocalDateTime syncedAt;
}
