package com.game.community.steam.service;

import com.game.community.model.entity.game.GameCatalog;
import com.game.community.model.mongo.SteamGameDetail;

public interface SteamGameDetailService {

    SteamGameDetail find(Long appId);

    boolean needsRefresh(SteamGameDetail detail);

    void saveFromCatalog(GameCatalog catalog);
}
