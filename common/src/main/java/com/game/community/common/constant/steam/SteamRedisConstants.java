package com.game.community.common.constant.steam;

/**
 * Steam 绑定 Redis Key 常量
 */
public final class SteamRedisConstants {

    public static final String BIND_STATE_KEY_PREFIX = "steam:bind:state:";

    public static final long BIND_STATE_TTL_SECONDS = 600L;

    public static final String GAME_DETAIL_KEY_PREFIX = "steam:game:detail:";

    /** 游戏详情缓存 TTL（秒） */
    public static final long GAME_DETAIL_TTL_SECONDS = 300L;

    /** 游戏基础信息缓存前缀。 */
    public static final String GAME_BASIC_INFO_KEY_PREFIX = "steam:game:basic:";

    /** 游戏基础信息缓存 TTL（秒）。 */
    public static final long GAME_BASIC_INFO_TTL_SECONDS = 604800L;

    /** 游戏基础信息补全锁前缀。 */
    public static final String GAME_BASIC_INFO_LOCK_PREFIX = "steam:game:basic:lock:";

    /** 游戏基础信息补全锁 TTL（秒）。 */
    public static final long GAME_BASIC_INFO_LOCK_SECONDS = 120L;

    public static final String GAME_CHART_KEY_PREFIX = "steam:game:chart:";

    public static final long GAME_CHART_TTL_SECONDS = 90000L;

    public static final String SYNC_LIBRARY_LOCK_PREFIX = "steam:sync:lock:";

    /** 同步游戏库锁 TTL（秒） */
    public static final long SYNC_LIBRARY_LOCK_SECONDS = 900L;

    /** 游戏库分页同步会话数据前缀。 */
    public static final String LIBRARY_SYNC_DATA_KEY_PREFIX = "steam:library:sync:data:";

    /** 游戏库分页同步会话有效期（秒）。 */
    public static final long LIBRARY_SYNC_DATA_TTL_SECONDS = 1800L;

    public static final String DETAIL_REFRESH_LOCK_PREFIX = "steam:detail:refresh:";

    public static final long DETAIL_REFRESH_LOCK_SECONDS = 90L;

    /** 游戏卡片指标/价格懒更新锁前缀。 */
    public static final String CATALOG_METRICS_PRICE_LOCK_PREFIX =
            "steam:catalog:metrics-price:lock:";

    /** 游戏卡片指标/价格懒更新锁有效期。 */
    public static final long CATALOG_METRICS_PRICE_LOCK_SECONDS = 120L;

    public static final String ACHIEVEMENT_REFRESH_LOCK_PREFIX = "steam:achievement:refresh:";

    public static final long ACHIEVEMENT_REFRESH_LOCK_SECONDS = 300L;

    public static final String USER_ACHIEVEMENT_REFRESH_LOCK_PREFIX = "steam:user-achievement:refresh:";

    public static final long USER_ACHIEVEMENT_REFRESH_LOCK_SECONDS = 300L;

    /** 榜单定时同步的分布式锁。 */
    public static final String CHART_SYNC_LOCK_KEY = "steam:chart:sync:lock";

    /** 榜单定时同步锁有效期（秒）。 */
    public static final long CHART_SYNC_LOCK_SECONDS = 900L;

    private SteamRedisConstants() {
    }
}
