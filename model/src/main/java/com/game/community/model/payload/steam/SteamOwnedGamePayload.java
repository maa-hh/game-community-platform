package com.game.community.model.payload.steam;

import lombok.Data;

import java.io.Serializable;

/** Steam 游戏库卡片接口返回的内部载荷。 */
@Data
public class SteamOwnedGamePayload implements Serializable {

    private long appId;

    private String name;

    private String nameZh;

    private String nameEn;

    private String iconUrl;

    private String coverUrl;

    private int playtimeForever;

    private int playtimeTwoWeeks;

    private long lastPlayedEpoch;
}
