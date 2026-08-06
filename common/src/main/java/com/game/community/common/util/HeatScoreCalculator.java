package com.game.community.common.util;

import com.game.community.common.constant.RecommendConstants;
import com.game.community.model.message.ArticleBehaviorMessage;

/**
 * 行为事件增量 → 热度分
 */
public final class HeatScoreCalculator {

    private HeatScoreCalculator() {
    }

    public static double deltaToScore(ArticleBehaviorMessage message) {
        if (message == null) {
            return 0D;
        }
        return safe(message.getViewDelta()) * RecommendConstants.VIEW_WEIGHT
                + safe(message.getLikeDelta()) * RecommendConstants.LIKE_WEIGHT
                + safe(message.getFavoriteDelta()) * RecommendConstants.FAVORITE_WEIGHT
                + safe(message.getShareDelta()) * RecommendConstants.SHARE_WEIGHT
                + safe(message.getCommentDelta()) * RecommendConstants.COMMENT_WEIGHT
                + safe(message.getCommentLikeDelta()) * RecommendConstants.COMMENT_LIKE_WEIGHT
                + safe(message.getReplyLikeDelta()) * RecommendConstants.REPLY_LIKE_WEIGHT;
    }

    private static long safe(Long value) {
        return value == null ? 0L : value;
    }
}
