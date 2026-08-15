package com.game.community.danmaku.common.constant;

/**
 * 弹幕服务内部 Redis key 前缀，避免不同业务入口拼出不一致的 key。
 */
public final class DanmakuCacheConstants {

    public static final String ID_KEY = "danmaku:id";
    public static final String SEQ_KEY_PREFIX = "danmaku:seq:";
    public static final String RATE_KEY_PREFIX = "danmaku:rate:";
    public static final String DEDUPE_KEY_PREFIX = "danmaku:dedupe:";
    public static final String DEDUPE_PROCESSING_VALUE = "__PROCESSING__";
    public static final String RECENT_KEY_PREFIX = "danmaku:recent:";
    public static final String MESSAGE_KEY_PREFIX = "danmaku:message:";
    public static final String REALTIME_CHANNEL_PREFIX = "danmaku:realtime:";

    private DanmakuCacheConstants() {
    }
}
