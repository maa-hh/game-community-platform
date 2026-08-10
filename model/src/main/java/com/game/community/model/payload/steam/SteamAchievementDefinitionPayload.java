package com.game.community.model.payload.steam;

import lombok.Data;

import java.io.Serializable;

/** Steam 官方成就定义接口返回的内部载荷。 */
@Data
public class SteamAchievementDefinitionPayload implements Serializable {

    private String apiName;

    private String name;

    private String description;

    private String iconUrl;
}
