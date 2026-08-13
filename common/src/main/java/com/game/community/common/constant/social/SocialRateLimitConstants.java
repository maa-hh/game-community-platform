package com.game.community.common.constant.social;

/**
 * 社交写操作的限流协议常量。
 *
 * <p>限流动作名属于接口行为标识，集中维护可以避免 Controller、配置和测试出现拼写漂移。</p>
 */
public final class SocialRateLimitConstants {

    public static final String COMMENT_CREATE = "comment:create";
    public static final String REPLY_CREATE = "reply:create";
    public static final String LIKE_ARTICLE = "like:article";
    public static final String LIKE_COMMENT = "like:comment";
    public static final String LIKE_REPLY = "like:reply";
    public static final String FAVORITE_ARTICLE = "favorite:article";
    public static final String SHARE_ARTICLE = "share:article";
    public static final String FOLLOW = "follow";
    public static final String BLACK = "black";
    public static final String REPORT_CREATE = "report:create";

    public static final int COMMENT_CREATE_LIMIT = 10;
    public static final int REPLY_CREATE_LIMIT = 20;
    public static final int LIKE_LIMIT = 120;
    public static final int FAVORITE_LIMIT = 60;
    public static final int SHARE_LIMIT = 30;
    public static final int FOLLOW_LIMIT = 30;
    public static final int REPORT_LIMIT = 5;

    public static final int SHORT_WINDOW_SECONDS = 60;
    public static final int REPORT_WINDOW_SECONDS = 300;
    public static final String REDIS_KEY_PREFIX = "social:rate:";
    public static final String ENABLED_PROPERTY = "social.rate-limit.enabled:true";
    public static final String FAIL_OPEN_PROPERTY = "social.rate-limit.fail-open:false";

    private SocialRateLimitConstants() {
    }
}
