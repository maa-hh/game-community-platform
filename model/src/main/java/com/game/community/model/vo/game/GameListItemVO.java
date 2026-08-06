package com.game.community.model.vo.game;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

@Data
public class GameListItemVO implements Serializable {

    private Long appId;

    private String name;

    private String coverUrl;

    private BigDecimal avgScore;

    private Integer reviewCount;

    private Integer discussCount;

    private List<String> genres;

    /** Steam 好评率 0–100 */
    private Integer steamReviewScore;

    /** Steam 评价总数 */
    private Integer steamReviewCount;

    private String developer;

    private String publisher;

    private String releaseDate;

    private GamePriceVO price;
}
