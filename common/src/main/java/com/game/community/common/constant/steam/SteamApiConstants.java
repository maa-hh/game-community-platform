package com.game.community.common.constant.steam;

/**
 * Steam Web API / OpenID 常量。
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
    /** Steam 新版成就图标资源地址，旧版 steamcommunity 路径对部分新游戏已返回 404。 */
    public static final String STEAM_ACHIEVEMENT_ICON_URL_PREFIX =
            "https://shared.akamai.steamstatic.com/community_assets/images/apps/";
    public static final String STEAM_STORE_APP_URL_PREFIX = "https://store.steampowered.com/app/";

    public static final int ACHIEVEMENT_SYNC_LIMIT = 60;
    public static final int ACHIEVEMENT_ENRICH_LIMIT = 30;
    public static final int CHART_BATCH_SIZE = 20;
    /** Steam Store 搜索榜单实际按 25 条一页返回，分页偏移也必须按 25 递增。 */
    public static final int CHART_REQUEST_PAGE_SIZE = 25;
    /** 榜单定时同步只预取前 100 条，后续由用户翻页时按需追加。 */
    public static final int CHART_MAX_BATCHES = 4;
    public static final int CHART_LIMIT = CHART_REQUEST_PAGE_SIZE * CHART_MAX_BATCHES;
    public static final int LIBRARY_SYNC_BATCH_SIZE = 20;
    public static final int LIBRARY_SYNC_MAX_BATCHES_PER_REQUEST = 5;
    public static final String LIBRARY_NAME_ZH_LANGUAGE = "schinese";
    public static final String LIBRARY_NAME_EN_LANGUAGE = "english";
    /** Steam 商店总评价必须使用 all，避免按界面语言过滤掉绝大多数评价。 */
    public static final String REVIEW_ALL_LANGUAGE = "all";
    /** Steam 评价汇总需要统计所有购买类型。 */
    public static final String REVIEW_ALL_PURCHASE_TYPE = "all";
    /** Steam 评价接口至少返回一条评价时才会返回稳定的汇总字段。 */
    public static final int REVIEW_REQUEST_PAGE_SIZE = 1;
    public static final String REVIEW_FILTER_ALL = "all";
    public static final String CHART_EXPAND_LOCK_PREFIX = "steam:chart:expand:";
    public static final long CHART_EXPAND_LOCK_SECONDS = 120L;
    public static final String STEAM_HEADER_IMAGE_URL_PREFIX =
            "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/";
    public static final int REVIEW_BACKFILL_BATCH_SIZE = 20;

    private SteamApiConstants() {
    }
}
