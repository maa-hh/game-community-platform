package com.game.community.common.constant;

public final class KafkaTopicConstants {

    public static final String ARTICLE_BEHAVIOR_TOPIC = "article-behavior-events";

    public static final String ARTICLE_BEHAVIOR_AGGREGATED_TOPIC = "article-behavior-aggregated";

    public static final String ARTICLE_SEARCH_SYNC_TOPIC = "article-search-sync";

    public static final String REPORT_AUDIT_TOPIC = "report-audit-events";

    public static final String SHOP_ORDER_PAID_TOPIC = "shop-order-paid-events";

    public static final String NOTIFICATION_EVENT_TOPIC = "notification-events";

    private KafkaTopicConstants() {
    }
}
