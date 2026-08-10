package com.game.community.model.payload.steam;

import lombok.Data;

import java.io.Serializable;

/** Steam 游戏视频接口返回的内部载荷。 */
@Data
public class SteamMoviePayload implements Serializable {

    private String name;

    private String thumbnailUrl;

    private String mp4Url;

    private String webmUrl;
}
