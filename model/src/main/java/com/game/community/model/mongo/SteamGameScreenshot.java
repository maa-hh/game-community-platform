package com.game.community.model.mongo;

import lombok.Data;

import java.io.Serializable;

/** Steam 游戏详情中的截图子文档。 */
@Data
public class SteamGameScreenshot implements Serializable {

    private String fullUrl;

    private String thumbnailUrl;
}
