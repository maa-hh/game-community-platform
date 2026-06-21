package com.game.community.common.constant;

public final class RecommendConstants {

    public static final String HOT_ARTICLE_RANK_KEY = "recommend:hot:article:rank";

    public static final String CATEGORY_HOT_ARTICLE_RANK_KEY_PREFIX = "recommend:hot:article:category:";

    public static final int HOT_RANK_SIZE = 150;

    public static final double LIKE_WEIGHT = 3D;

    public static final double COMMENT_WEIGHT = 5D;

    public static final double COMMENT_LIKE_WEIGHT = 1D;

    public static final double REPLY_WEIGHT = 2D;

    public static final double REPLY_LIKE_WEIGHT = 0.5D;

    public static final double VIEW_WEIGHT = 1D;

    private RecommendConstants() {
    }
}
