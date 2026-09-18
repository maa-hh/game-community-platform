package com.game.community.common.constant.shop;

public final class ShopRedisConstants {

    public static final String STOCK_KEY_PREFIX = "shop:stock:";

    public static final String LIMIT_KEY_PREFIX = "shop:limit:";

    public static final String LIMIT_LAST_KEY_PREFIX = "shop:limit:last:";

    public static final String LIMIT_WINDOW_KEY_PREFIX = "shop:limit:window:";

    public static final String LIMIT_RESERVATION_KEY_PREFIX = "shop:limit:reservation:";

    public static final String ONCE_BITMAP_KEY_PREFIX = "shop:limit:once:bitmap:";

    public static final String ORDER_STATE_KEY_PREFIX = "shop:order:state:";

    public static final String ORDER_EXPIRE_QUEUE = "shop:order:expire:queue";

    public static final String RECONCILE_LOCK = "shop:consistency:reconcile:lock";

    public static final long STOCK_TTL_SECONDS = 3600;

    public static final long RESERVATION_TTL_SECONDS = 900;

    public static final long ORDER_STATE_TTL_SECONDS = 1800;

    public static final long LIMIT_TTL_SECONDS = 86400;

    public static final long RECONCILE_LOCK_TTL_SECONDS = 300;

    public static String stockKey(Long itemId) {
        return STOCK_KEY_PREFIX + "{" + itemId + "}";
    }

    public static String limitKey(Long itemId, Long userId) {
        return LIMIT_KEY_PREFIX + "{" + itemId + "}:" + userId;
    }

    public static String limitLastKey(Long itemId, Long userId) {
        return LIMIT_LAST_KEY_PREFIX + "{" + itemId + "}:" + userId;
    }

    public static String limitWindowKey(Long itemId, Long userId) {
        return LIMIT_WINDOW_KEY_PREFIX + "{" + itemId + "}:" + userId;
    }

    public static String limitReservationKey(Long itemId, Long userId, String orderNo) {
        return LIMIT_RESERVATION_KEY_PREFIX + "{" + itemId + "}:" + userId + ":" + orderNo;
    }

    public static String onceBitmapKey(Long itemId) {
        return ONCE_BITMAP_KEY_PREFIX + "{" + itemId + "}";
    }

    private ShopRedisConstants() {
    }
}
