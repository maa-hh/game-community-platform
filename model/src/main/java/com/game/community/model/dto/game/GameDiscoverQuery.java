package com.game.community.model.dto.game;

import lombok.Data;

import java.io.Serializable;

@Data
public class GameDiscoverQuery implements Serializable {

    /** all / hot / new / free / discount */
    private String board = "all";

    /** steam_score / steam_reviews / price / discount_price / rank */
    private String sort;

    /** asc / desc */
    private String order = "desc";

    private Long page = 1L;

    private Long size = 20L;

    private Integer minSteamScore;

    private Integer maxSteamScore;

    private Integer minSteamReviews;

    private Integer maxSteamReviews;

    /** 原价（分） */
    private Integer minPrice;

    private Integer maxPrice;

    /** 现价（分） */
    private Integer minFinalPrice;

    private Integer maxFinalPrice;

    private Integer minDiscount;

    private Boolean discountOnly;

    private Boolean freeOnly;
}
