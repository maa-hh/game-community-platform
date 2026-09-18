package com.game.community.recommend.util;

import com.game.community.common.constant.RecommendConstants;
import com.game.community.common.util.HeatScoreCalculator;
import com.game.community.model.message.ArticleBehaviorMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HeatScoreCalculatorTest {

    @Test
    void includesDanmakuAndInteractionLikeDeltasInOneFormula() {
        ArticleBehaviorMessage message = new ArticleBehaviorMessage();
        message.setViewDelta(2L);
        message.setLikeDelta(1L);
        message.setCommentDelta(1L);
        message.setDanmakuDelta(1L);
        message.setCommentLikeDelta(1L);
        message.setReplyLikeDelta(1L);

        double expected = 2D * RecommendConstants.VIEW_WEIGHT
                + RecommendConstants.LIKE_WEIGHT
                + RecommendConstants.COMMENT_WEIGHT
                + RecommendConstants.DANMAKU_WEIGHT
                + RecommendConstants.COMMENT_LIKE_WEIGHT
                + RecommendConstants.REPLY_LIKE_WEIGHT;
        assertEquals(expected, HeatScoreCalculator.deltaToScore(message));
    }
}
