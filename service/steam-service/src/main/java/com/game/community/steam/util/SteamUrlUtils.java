package com.game.community.steam.util;

import org.springframework.util.StringUtils;

/** Steam 外部资源地址的统一兼容处理。 */
public final class SteamUrlUtils {

    private SteamUrlUtils() {
    }

    /** 将历史接口或数据库中的 HTTP 头像升级为 HTTPS，避免移动端混合内容拦截。 */
    public static String normalizeAvatarUrl(String avatarUrl) {
        if (!StringUtils.hasText(avatarUrl)) {
            return null;
        }
        String normalized = avatarUrl.trim();
        return normalized.startsWith("http://")
                ? "https://" + normalized.substring("http://".length())
                : normalized;
    }
}
