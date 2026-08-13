package com.game.community.common.constant;

public final class KafkaTopicConstants {

    public static final String ARTICLE_BEHAVIOR_TOPIC = "article-behavior-events";

    public static final String ARTICLE_SEARCH_SYNC_TOPIC = "article-search-sync";

    /** Steam 游戏轻量索引同步事件。 */
    public static final String GAME_SEARCH_SYNC_TOPIC = "game-search-sync";

    public static final String REPORT_AUDIT_TOPIC = "report-audit-events";

    /** 弹幕可靠接收事件；实时广播不依赖该 topic 的消费时延。 */
    public static final String DANMAKU_TOPIC = "danmaku-events";

    /** 商城支付成功事件；下游权益服务按 orderNo 幂等消费。 */
    public static final String SHOP_ORDER_PAID_TOPIC = "shop-order-paid-events";

    public static final String MODERATION_TASK_TOPIC = "moderation-task-events";

    public static final String NOTIFICATION_EVENT_TOPIC = "notification-events";

    /** 通知聚合/直通后的就绪事件（由 notification-service Kafka Streams 写出） */
    public static final String NOTIFICATION_EVENT_READY_TOPIC = "notification-events-ready";

    /** 通知事件格式或接收人无效时的隔离 topic，避免静默丢失。 */
    public static final String NOTIFICATION_EVENT_INVALID_TOPIC = "notification-events-invalid";

    private KafkaTopicConstants() {
    }
}
