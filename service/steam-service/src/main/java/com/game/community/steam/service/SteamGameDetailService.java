package com.game.community.steam.service;

import com.game.community.model.entity.game.GameCatalog;
import com.game.community.model.mongo.SteamGameDetail;

public interface SteamGameDetailService {

    /** 查询 Mongo 中保存的 Steam 富详情。 */
    SteamGameDetail find(Long appId);

    /** 判断富详情是否需要刷新。 */
    boolean needsRefresh(SteamGameDetail detail);

    /** 从结构化游戏目录保存富详情。 */
    void saveFromCatalog(GameCatalog catalog);
}
