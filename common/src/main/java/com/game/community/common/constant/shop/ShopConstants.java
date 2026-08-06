package com.game.community.common.constant.shop;

public final class ShopConstants {

    public static final long DEFAULT_POINTS = 5000L;

    public static final int ORDER_MAX_QUANTITY = 10;

    public static final long ORDER_EXPIRE_MINUTES = 15;

    public static final String ORDER_PREFIX = "SO";

    public static final String DELIVERY_SOURCE_TYPE = "SHOP";

    public static final String EMPTY_TEXT = "";

    public static final String EPOCH_TIME = "1970-01-01T00:00:00";

    public static final int ITEM_OFF_SHELF = 0;

    public static final int ITEM_ON_SHELF = 1;

    public static final int ORDER_FAILED = -1;

    public static final int ORDER_CANCELLED = 0;

    public static final int ORDER_CREATING = 1;

    public static final int ORDER_PENDING_PAY = 2;

    public static final int ORDER_PAID = 3;

    public static final int ORDER_COMPLETED = 4;

    public static final int STOCK_UNLIMITED = -1;

    public static final int LIMIT_UNLIMITED = -1;

    public static final int LIMIT_WINDOW_DISABLED = 0;

    /** 复购策略 */
    public static final class RepurchasePolicy {
        public static final String ONCE_FOREVER = "ONCE_FOREVER";
        public static final String UNLIMITED = "UNLIMITED";
        public static final String COOLDOWN = "COOLDOWN";
        public static final String LIMIT_PER_WINDOW = "LIMIT_PER_WINDOW";

        private RepurchasePolicy() {
        }
    }

    public static final class PointsBizType {
        public static final String SHOP_EXCHANGE = "SHOP_EXCHANGE";
        public static final String SHOP_REFUND = "SHOP_REFUND";
        public static final String ADMIN_ADJUST = "ADMIN_ADJUST";

        private PointsBizType() {
        }
    }

    private ShopConstants() {
    }
}
