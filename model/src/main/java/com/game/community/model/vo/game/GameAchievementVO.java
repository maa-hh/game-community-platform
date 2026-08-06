package com.game.community.model.vo.game;

import lombok.Data;

import java.io.Serializable;

@Data
public class GameAchievementVO implements Serializable {

    private String apiName;

    private String name;

    private String description;

    private String iconUrl;
}
