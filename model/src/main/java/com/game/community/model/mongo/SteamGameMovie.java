package com.game.community.model.mongo;

import lombok.Data;

import java.io.Serializable;

/** Steam 游戏详情中的视频子文档。 */
@Data
public class SteamGameMovie implements Serializable {

    private String name;

    private String thumbnailUrl;

    private String mp4Url;

    private String webmUrl;
}
