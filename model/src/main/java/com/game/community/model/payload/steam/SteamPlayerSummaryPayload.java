package com.game.community.model.payload.steam;

import lombok.Data;

import java.io.Serializable;

/** Steam 用户公开资料接口返回的内部载荷。 */
@Data
public class SteamPlayerSummaryPayload implements Serializable {

    private String steamId;

    private String personaName;

    private String avatarUrl;

    private String profileUrl;
}
