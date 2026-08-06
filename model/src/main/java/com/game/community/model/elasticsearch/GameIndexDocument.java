package com.game.community.model.elasticsearch;

import com.game.community.model.vo.game.GamePriceVO;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/** 游戏搜索读模型，只保存搜索、列表和标签所需的轻量字段。 */
@Data
public class GameIndexDocument implements Serializable {

    private Long appId;

    private String name;

    private List<String> aliases;

    private String shortDescription;

    private List<String> developers;

    private List<String> publishers;

    private List<String> genres;

    private String coverUrl;

    private String releaseDate;

    private Integer steamReviewScore;

    private Integer steamReviewCount;

    private java.math.BigDecimal avgScore;

    private Integer reviewCount;

    private Integer discussCount;

    private GamePriceVO price;

    private Integer status;

    private Boolean detailReady;

    private LocalDateTime updatedAt;
}
