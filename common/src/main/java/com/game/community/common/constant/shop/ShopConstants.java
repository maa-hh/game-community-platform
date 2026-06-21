package com.game.community.common.constant.shop;

public final class ShopConstants {

    public static final long ORDER_EXPIRE_MINUTES = 15;

    public static final int ITEM_OFF_SHELF = 0;

    public static final int ITEM_ON_SHELF = 1;

    public static final int PRODUCT_TYPE_COUPON = 0;

    public static final int PRODUCT_TYPE_SKIN = 2;

    public static final int PRODUCT_TYPE_ITEM = 3;

    public static final int ORDER_FAILED = -1;

    public static final int ORDER_CANCELLED = 0;

    public static final int ORDER_CREATING = 1;

    public static final int ORDER_PENDING_PAY = 2;

    public static final int ORDER_PAID = 3;

    public static final int ORDER_COMPLETED = 4;

    public static final int PAY_GOLD = 0;

    public static final int PAY_DIAMOND = 1;

    public static final int COUPON_STATUS_UNUSED = 0;

    public static final int COUPON_STATUS_USED = 1;

    public static final int COUPON_STATUS_EXPIRED = 2;

    public static final int COUPON_STATUS_LOCKED = 3;

    public static final int COUPON_DISCOUNT_AMOUNT = 1;

    public static final int COUPON_DISCOUNT_RATE = 2;

    public static final int COUPON_SCOPE_ALL = -1;

    public static final int STOCK_UNLIMITED = -1;

    public static final int LIMIT_UNLIMITED = -1;

    private ShopConstants() {
    }
}
