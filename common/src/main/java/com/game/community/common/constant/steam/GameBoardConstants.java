package com.game.community.common.constant.steam;

import java.util.Set;

/**
 * 游戏发现 / 榜单 board 名称
 */
public final class GameBoardConstants {

    public static final String ALL = "all";

    public static final String HOT = "hot";

    public static final String NEW = "new";

    public static final String FREE = "free";

    public static final String DISCOUNT = "discount";

    public static final Set<String> DISCOVER_BOARDS =
            Set.of(ALL, HOT, NEW, FREE, DISCOUNT);

    public static final Set<String> CHART_BOARDS =
            Set.of(HOT, NEW, FREE, DISCOUNT);

    public static final Set<String> SORT_FIELDS =
            Set.of("steam_score", "steam_reviews", "price", "discount_price", "rank");

    public static final String FEATURED_SECTION_TOP_SELLERS = "top_sellers";

    public static final String FEATURED_SECTION_NEW_RELEASES = "new_releases";

    public static final String FEATURED_SECTION_SPECIALS = "specials";

    private GameBoardConstants() {
    }
}
