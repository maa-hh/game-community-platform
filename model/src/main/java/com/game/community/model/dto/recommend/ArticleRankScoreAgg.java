package com.game.community.model.dto.recommend;

import lombok.Data;

import java.io.Serializable;

@Data
public class ArticleRankScoreAgg implements Serializable {

    private Long articleId;

    private Double totalScore;
}
