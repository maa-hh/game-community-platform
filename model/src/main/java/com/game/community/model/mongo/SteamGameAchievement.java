package com.game.community.model.mongo;

import lombok.Data;

import java.io.Serializable;

/** Steam 游戏详情中的成就子文档。 */
@Data
public class SteamGameAchievement implements Serializable {

    private String apiName;

    private String name;

    private String description;

    private String iconUrl;

    private Double globalPercent;
}
