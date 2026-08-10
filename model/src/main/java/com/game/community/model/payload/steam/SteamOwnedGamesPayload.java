package com.game.community.model.payload.steam;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/** Steam 游戏库接口返回的内部载荷。 */
@Data
public class SteamOwnedGamesPayload implements Serializable {

    private int gameCount;

    private boolean libraryPublic;

    private List<SteamOwnedGamePayload> games;
}
