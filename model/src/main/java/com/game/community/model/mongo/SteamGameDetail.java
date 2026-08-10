package com.game.community.model.mongo;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/** Steam 游戏富详情，描述、媒体和成就等非检索大字段存 MongoDB。 */
@Data
@Document(collection = "steam_game_detail")
public class SteamGameDetail implements Serializable {

    @Id
    private String id;

    @Indexed(name = "uk_steam_game_detail_app_id", unique = true)
    private Long appId;

    private String steamShortDesc;

    private String steamAboutHtml;

    private List<SteamGameScreenshot> screenshots;

    private List<SteamGameMovie> movies;

    private List<String> categories;

    private Integer metacriticScore;

    private String metacriticUrl;

    private Integer achievementTotal;

    private List<SteamGameAchievement> achievementHighlights;

    private String pcRequirementsMin;

    private String pcRequirementsRec;

    private LocalDateTime richSyncedAt;

    private LocalDateTime updateTime;
}
