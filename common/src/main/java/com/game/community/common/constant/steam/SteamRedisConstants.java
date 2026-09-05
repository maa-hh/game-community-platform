package com.game.community.common.constant.steam;

/**
 * Steam Service 使用的 Redis Key 与 TTL 常量。
 */
public final class SteamRedisConstants {

    public static final String BIND_STATE_KEY_PREFIX = "steam:bind:state:";
    public static final long BIND_STATE_TTL_SECONDS = 600L;
    public static final String GAME_DETAIL_KEY_PREFIX = "steam:game:detail:";
    public static final long GAME_DETAIL_TTL_SECONDS = 300L;
    public static final String GAME_BASIC_INFO_KEY_PREFIX = "steam:game:basic:";
    public static final long GAME_BASIC_INFO_TTL_SECONDS = 604800L;
    /** Steam 商店不存在的 App 负缓存，避免启动预热反复请求失效 ID。 */
    public static final String GAME_BASIC_INFO_UNAVAILABLE_KEY_PREFIX =
            "steam:game:basic:unavailable:";
    public static final long GAME_BASIC_INFO_UNAVAILABLE_TTL_SECONDS = 86400L;
    public static final String GAME_BASIC_INFO_LOCK_PREFIX = "steam:game:basic:lock:";
    public static final long GAME_BASIC_INFO_LOCK_SECONDS = 120L;
    public static final String GAME_CHART_KEY_PREFIX = "steam:game:chart:";
    public static final long GAME_CHART_TTL_SECONDS = 90000L;
    public static final String SYNC_LIBRARY_LOCK_PREFIX = "steam:sync:lock:";
    public static final long SYNC_LIBRARY_LOCK_SECONDS = 900L;
    public static final String LIBRARY_SYNC_DATA_KEY_PREFIX = "steam:library:sync:data:";
    public static final long LIBRARY_SYNC_DATA_TTL_SECONDS = 1800L;
    public static final String DETAIL_REFRESH_LOCK_PREFIX = "steam:detail:refresh:";
    public static final long DETAIL_REFRESH_LOCK_SECONDS = 90L;
    public static final String CATALOG_METRICS_PRICE_LOCK_PREFIX =
            "steam:catalog:metrics-price:lock:";
    public static final long CATALOG_METRICS_PRICE_LOCK_SECONDS = 120L;
    public static final String ACHIEVEMENT_REFRESH_LOCK_PREFIX = "steam:achievement:refresh:";
    public static final long ACHIEVEMENT_REFRESH_LOCK_SECONDS = 300L;
    public static final String USER_ACHIEVEMENT_REFRESH_LOCK_PREFIX = "steam:user-achievement:refresh:";
    public static final long USER_ACHIEVEMENT_REFRESH_LOCK_SECONDS = 300L;
    public static final String CHART_SYNC_LOCK_KEY = "steam:chart:sync:lock";
    public static final long CHART_SYNC_LOCK_SECONDS = 900L;
    public static final String CATALOG_DAILY_SYNC_LOCK_KEY = "steam:catalog:daily-sync";
    public static final long CATALOG_DAILY_SYNC_LOCK_SECONDS = 3600L;

    private SteamRedisConstants() {
    }
}
