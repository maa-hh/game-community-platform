package com.game.community.model.payload.steam;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/** Steam 商店基础信息接口返回的内部载荷，不包含富详情和更新状态。 */
@Data
public class SteamGameBasicPayload implements Serializable {

    private Long appId;

    private String steamName;

    private String nameZh;

    private String nameEn;

    private String displayName;

    private String headerImage;

    private List<String> developers;

    private List<String> publishers;

    private List<String> genres;

    private String releaseDate;

    private String steamUrl;

    private Boolean steamIsFree;
}
