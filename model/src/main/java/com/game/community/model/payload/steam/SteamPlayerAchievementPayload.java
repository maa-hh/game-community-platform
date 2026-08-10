package com.game.community.model.payload.steam;

import lombok.Data;

import java.io.Serializable;

/** Steam 玩家成就状态接口返回的内部载荷。 */
@Data
public class SteamPlayerAchievementPayload implements Serializable {

    private String apiName;

    private boolean unlocked;

    private long unlockEpoch;
}
