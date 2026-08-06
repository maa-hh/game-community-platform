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

    public static final String GAME_CHART_KEY_PREFIX = "steam:game:chart:";

    public static final long GAME_CHART_TTL_SECONDS = 90000L;

    public static final String SYNC_LIBRARY_LOCK_PREFIX = "steam:sync:lock:";

    /** 同步游戏库锁 TTL（秒） */
    public static final long SYNC_LIBRARY_LOCK_SECONDS = 900L;

    public static final String DETAIL_REFRESH_LOCK_PREFIX = "steam:detail:refresh:";

    public static final long DETAIL_REFRESH_LOCK_SECONDS = 90L;

    private SteamRedisConstants() {
    }
}
