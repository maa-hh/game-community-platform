package com.game.community.steam.service;

import com.game.community.model.mongo.SteamGameDetail;
import com.game.community.model.payload.steam.SteamGameDetailsPayload;

public interface SteamGameDetailService {

    /** 查询 Mongo 中保存的 Steam 富详情。 */
    SteamGameDetail find(Long appId);

    /** 判断富详情是否需要刷新。 */
    boolean needsRefresh(SteamGameDetail detail);

    /** 保存 Steam 富详情载荷，正文和媒体写入 MongoDB。 */
    void save(SteamGameDetailsPayload payload);
}
