package com.game.community.model.payload.steam;

import lombok.Data;

import java.io.Serializable;

/** Steam 榜单或轻量搜索返回的游戏卡片内部载荷。 */
@Data
public class SteamChartGamePayload implements Serializable {

    private Long appId;

    private String name;

    private String coverUrl;

    private SteamPricePayload price;
}
