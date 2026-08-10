package com.game.community.model.vo.game;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class GameDetailVO implements Serializable {

    private Long appId;

    private String name;

    private String shortDescription;

    private String aboutHtml;

    private String headerImage;

    private List<String> developers;

    private List<String> publishers;

    private List<String> genres;

    private String releaseDate;

    private String steamUrl;

    private Integer discussCount;

    private GameRatingStatsVO rating;

    /** Steam 好评率 0–100 */
    private Integer steamReviewScore;

    /** Steam 评价总数。 */
    private Integer steamReviewCount;

    private List<GameScreenshotVO> screenshots;

    private List<GameMovieVO> movies;

    private List<String> categories;

    private GamePriceVO price;

    private GameMetacriticVO metacritic;

    private Integer achievementTotal;

    private List<GameAchievementVO> achievementHighlights;

    private String pcRequirementsMin;

    private String pcRequirementsRec;

    private LocalDateTime steamSyncedAt;
}
