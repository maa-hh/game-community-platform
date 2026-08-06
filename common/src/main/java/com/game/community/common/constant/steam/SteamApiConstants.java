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

    /** 与前端游戏榜单每页 18 条对齐，首批快照完整覆盖 3 页。 */
    public static final int CHART_LIMIT = 18 * 3;

    public static final int REVIEW_BACKFILL_BATCH_SIZE = 20;

    private SteamApiConstants() {
    }
}
