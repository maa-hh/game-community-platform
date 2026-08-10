package com.game.community.model.payload.steam;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/** Steam 商店详情接口返回的内部载荷，不包含数据库更新时间和刷新状态。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SteamGameDetailsPayload extends SteamGameBasicPayload {

    private String shortDescription;

    private String aboutHtml;

    private List<SteamScreenshotPayload> screenshots;

    private List<SteamMoviePayload> movies;

    private List<String> categories;

    private Integer steamReviewScore;

    private Integer steamReviewCount;

    private Integer priceInitial;

    private Integer priceFinal;

    private Integer priceDiscount;

    private Long priceDiscountEndAt;

    private String priceCurrency;

    private String priceFormatted;

    private Integer metacriticScore;

    private String metacriticUrl;

    private Integer achievementTotal;

    private List<SteamAchievementDefinitionPayload> achievementHighlights;

    private String pcRequirementsMin;

    private String pcRequirementsRec;
}
