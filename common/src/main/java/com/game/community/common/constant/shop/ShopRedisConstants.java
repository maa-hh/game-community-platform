package com.game.community.common.constant.shop;

public final class ShopRedisConstants {

    public static final String STOCK_KEY_PREFIX = "shop:stock:";

    public static final String LIMIT_KEY_PREFIX = "shop:limit:";

    public static final String REQUEST_KEY_PREFIX = "shop:request:";

    public static final String ORDER_STATE_KEY_PREFIX = "shop:order:state:";

    public static final String ORDER_CREATE_QUEUE = "shop:order:create:queue";

    public static final String ORDER_EXPIRE_QUEUE = "shop:order:expire:queue";

    public static final long STOCK_TTL_SECONDS = 3600;

    public static final long REQUEST_TTL_SECONDS = 900;

    public static final long ORDER_STATE_TTL_SECONDS = 1800;

    public static final long LIMIT_TTL_SECONDS = 86400;

    private ShopRedisConstants() {
    }
}
