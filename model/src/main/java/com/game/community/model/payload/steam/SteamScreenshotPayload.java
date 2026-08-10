package com.game.community.model.payload.steam;

import lombok.Data;

import java.io.Serializable;

/** Steam 游戏截图接口返回的内部载荷。 */
@Data
public class SteamScreenshotPayload implements Serializable {

    private String fullUrl;

    private String thumbnailUrl;
}
