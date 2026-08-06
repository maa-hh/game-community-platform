package com.game.community.model.vo.game;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class GameRatingStatsVO implements Serializable {

    private Integer reviewCount;

    private BigDecimal avgScore;
}
