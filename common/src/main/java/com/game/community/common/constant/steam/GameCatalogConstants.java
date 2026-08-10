package com.game.community.common.constant.steam;

/**
 * 游戏百科目录常量。
 */
public final class GameCatalogConstants {

    public static final int STALE_DAYS = 7;
    public static final long METRICS_TTL_DAYS = 1L;
    public static final long PRICE_TTL_DAYS = 1L;
    public static final int METRICS_REFRESH_BATCH_SIZE = 100;
    public static final long REFRESH_GRACE_MINUTES = 1L;

    public static final String DESC_SOURCE_COMMUNITY_FIRST = "COMMUNITY_FIRST";
    public static final String FOLLOW_SOURCE_MANUAL = "manual";
    public static final String FOLLOW_SOURCE_DISCOVER = "discover";
    public static final String FOLLOW_SOURCE_STEAM_IMPORT = "steam_import";

    private GameCatalogConstants() {
    }
}
