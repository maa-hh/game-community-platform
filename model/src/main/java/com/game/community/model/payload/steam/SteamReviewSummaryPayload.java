package com.game.community.model.payload.steam;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** Steam 评价汇总接口返回的内部载荷。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SteamReviewSummaryPayload implements Serializable {

    private Integer positivePercent;

    private Integer totalReviews;
}
