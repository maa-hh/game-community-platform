package com.game.community.common.constant.gateway;

import java.util.List;

/**
 * 网关透传请求头常量
 */
public class GatewayConstants {

    public static final String USER_ID_HEADER = "X-User-Id";

    public static final String USER_TYPE_HEADER = "X-User-Type";

    public static final String STEAM_ACCOUNT_HEADER = "X-Steam-Account";

    public static final String SESSION_ID_HEADER = "X-Session-Id";

    public static final String INTERNAL_SECRET_HEADER = "X-Gateway-Internal-Secret";

    /**
     * 无需 access JWT 的认证接口前缀（网关转发至 user-service /user/auth/*）
     * <p>
     * logout 也在白名单：access 过期时仍可凭 refresh Cookie 作废会话
     */
    public static final List<String> AUTH_PUBLIC_PATH_PREFIXES = List.of(
            "/user/auth/",
            "/steam/callback",
            "/auth/"  // 兼容旧路径，待全量切换后可删
    );

    /** @deprecated 使用 {@link #AUTH_PUBLIC_PATH_PREFIXES} 前缀匹配 */
    @Deprecated
    public static final List<String> AUTH_PUBLIC_PATHS = List.of(
            "/user/auth/send-code",
            "/user/auth/register",
            "/user/auth/login",
            "/user/auth/refresh",
            "/user/auth/reset-password",
            "/user/auth/logout"
    );

    /**
     * 无需 access JWT 的公开路径前缀（如 OG 分享落地页）
     */
    public static final String SHARE_POST_PATH_PREFIX = "/share/post/";

    /**
     * 游客可读前缀：帖子详情 / 最新列表 / 分区 / 社交只读统计与评论树 / 批量用户名片
     * <p>
     * 写操作仍走 @LoginCheck；网关仅放行「可不带 token」的路径。
     */
    public static final List<String> PUBLIC_READ_PATH_PREFIXES = List.of(
            "/share/post/",
            "/article/",
            "/category/",
            "/social/comment/",
            "/social/reply/",
            "/social/article/count",
            "/social/article/counts",
            "/social/like/article/check/",
            "/social/like/comment/check/",
            "/social/like/reply/check/",
            "/social/favorite/article/check/",
            "/search/article",
            "/search/game",
            "/user/ids",
            "/user/cosmetic/decorations/batch",
            "/hot-article/",
            "/game/",
            "/danmaku/history/",
            "/danmaku/ws/"
    );

    private GatewayConstants() {
    }
}
