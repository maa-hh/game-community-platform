package com.game.community.common.constant;

public final class RecommendConstants {

    public static final String RANK_KEY_PREFIX = "recommend:rank:";

    public static final String SNAPSHOT_KEY_PREFIX = "recommend:snapshot:";

    /** 历史周期被动缓存（查库后写入，带 TTL，非实时榜 Key） */
    public static final String PASSIVE_CACHE_KEY_PREFIX = "recommend:cache:rank:v2:";

    /** 被动缓存默认 TTL（秒） */
    public static final long PASSIVE_CACHE_TTL_SECONDS = 3600L;

    public static final String CATEGORY_SCOPE_ALL = "all";

    public static final String CATEGORY_SCOPE_PREFIX = "cat:";

    public static final String BEHAVIOR_CONSUMER_GROUP = "recommend-service-hot-rank";

    public static final int HOT_RANK_SIZE = 100;

    public static final double VIEW_WEIGHT = 1D;

    public static final double LIKE_WEIGHT = 2D;

    public static final double FAVORITE_WEIGHT = 2D;

    public static final double SHARE_WEIGHT = 3D;

    public static final double COMMENT_WEIGHT = 5D;

    /** 评论点赞权重 */
    public static final double COMMENT_LIKE_WEIGHT = 1D;

    /** 回复点赞权重 */
    public static final double REPLY_LIKE_WEIGHT = 1D;

    /** @deprecated 旧公式权重，仅兼容历史文档 */
    @Deprecated
    public static final double COMMENT_LIKE_WEIGHT_LEGACY = 1D;

    /** @deprecated 旧公式权重，仅兼容历史文档 */
    @Deprecated
    public static final double REPLY_WEIGHT = 2D;

    /** @deprecated 旧公式权重，仅兼容历史文档 */
    @Deprecated
    public static final double REPLY_LIKE_WEIGHT_LEGACY = 0.5D;

    public static String categoryScope(Long categoryId) {
        return categoryId == null ? CATEGORY_SCOPE_ALL : CATEGORY_SCOPE_PREFIX + categoryId;
    }

    public static String rankKey(String board, String periodSegment, String categoryScope) {
        return RANK_KEY_PREFIX + board + ":" + periodSegment + ":" + categoryScope;
    }

    public static String snapshotKey(String board, String periodKey, String categoryScope) {
        return SNAPSHOT_KEY_PREFIX + board + ":" + periodKey + ":" + categoryScope;
    }

    public static String passiveCacheKey(String board, String periodKey, String categoryScope) {
        return PASSIVE_CACHE_KEY_PREFIX + board + ":" + periodKey + ":" + categoryScope;
    }

    private RecommendConstants() {
    }
}
