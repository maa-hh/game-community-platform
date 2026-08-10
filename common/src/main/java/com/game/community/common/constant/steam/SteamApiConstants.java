package com.game.community.common.constant.steam;

/**
 * Steam Web API / OpenID 常量
 */
public final class SteamApiConstants {

    public static final String OPENID_ENDPOINT = "https://steamcommunity.com/openid/login";

    public static final String OPENID_CLAIMED_ID_PREFIX = "https://steamcommunity.com/openid/id/";

    public static final String PLAYER_SUMMARIES_URL =
            "https://api.steampowered.com/ISteamUser/GetPlayerSummaries/v2/";

    public static final String STEAM_LEVEL_URL =
            "https://api.steampowered.com/IPlayerService/GetSteamLevel/v1/";

    public static final String OWNED_GAMES_URL =
            "https://api.steampowered.com/IPlayerService/GetOwnedGames/v1/";

    public static final String PLAYER_ACHIEVEMENTS_URL =
            "https://api.steampowered.com/ISteamUserStats/GetPlayerAchievements/v0001/";

    public static final String GLOBAL_ACHIEVEMENTS_URL =
            "https://api.steampowered.com/ISteamUserStats/GetGlobalAchievementPercentagesForApp/v2/";

    public static final String GAME_SCHEMA_URL =
            "https://api.steampowered.com/ISteamUserStats/GetSchemaForGame/v2/";

    public static final String APP_DETAILS_URL = "https://store.steampowered.com/api/appdetails";

    public static final String APP_REVIEWS_URL = "https://store.steampowered.com/appreviews";

    public static final String STORE_BROWSE_ITEMS_URL =
            "https://api.steampowered.com/IStoreBrowseService/GetItems/v1";

    public static final String FEATURED_CATEGORIES_URL =
            "https://store.steampowered.com/api/featuredcategories/";

    public static final String STORE_SEARCH_URL =
            "https://store.steampowered.com/search/results/";

    public static final String STEAM_ICON_URL_PREFIX =
            "https://media.steampowered.com/steamcommunity/public/images/apps/";

    public static final String STEAM_STORE_APP_URL_PREFIX = "https://store.steampowered.com/app/";

    public static final int ACHIEVEMENT_SYNC_LIMIT = 60;

    public static final int ACHIEVEMENT_ENRICH_LIMIT = 30;

    /** 榜单后台抓取和落库的批次大小，与前端榜单页大小对齐。 */
    public static final int CHART_BATCH_SIZE = 20;

    /** 每个榜单最多抓取的批次数量。 */
    public static final int CHART_MAX_BATCHES = 5;

    /** 榜单快照单次最多维护的游戏数量。 */
    public static final int CHART_LIMIT = CHART_BATCH_SIZE * CHART_MAX_BATCHES;

    /** Steam 游戏库同步每页处理的游戏数量。 */
    public static final int LIBRARY_SYNC_BATCH_SIZE = 20;

    /** 单次前端同步请求最多连续提交的游戏库批次数量。 */
    public static final int LIBRARY_SYNC_MAX_BATCHES_PER_REQUEST = 5;

    /** Steam 游戏库中文名称请求语言。 */
    public static final String LIBRARY_NAME_ZH_LANGUAGE = "schinese";

    /** Steam 游戏库英文名称请求语言。 */
    public static final String LIBRARY_NAME_EN_LANGUAGE = "english";

    /** Steam 游戏卡片头图地址前缀，不需要请求游戏详情接口。 */
    public static final String STEAM_HEADER_IMAGE_URL_PREFIX =
            "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/";

    public static final int REVIEW_BACKFILL_BATCH_SIZE = 20;

    private SteamApiConstants() {
    }
}
