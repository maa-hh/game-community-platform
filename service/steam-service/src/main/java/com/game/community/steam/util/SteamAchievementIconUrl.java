package com.game.community.steam.util;

import com.game.community.common.constant.steam.SteamApiConstants;
import org.springframework.util.StringUtils;

/** Steam 成就图标地址转换，统一新地址并兼容旧版输入。 */
public final class SteamAchievementIconUrl {

    private static final String LEGACY_PATH = "/steamcommunity/public/images/apps/";
    private static final String CURRENT_PATH = "/community_assets/images/apps/";

    private SteamAchievementIconUrl() {
    }

    /** 将数据库或 Steam 返回的旧地址迁移为新版 community_assets 地址。 */
    public static String toCurrent(String iconUrl) {
        if (!StringUtils.hasText(iconUrl)) {
            return iconUrl;
        }
        String value = stripQuery(iconUrl.trim());
        int legacyIndex = value.indexOf(LEGACY_PATH);
        if (legacyIndex >= 0) {
            return SteamApiConstants.STEAM_ACHIEVEMENT_ICON_URL_PREFIX
                    + value.substring(legacyIndex + LEGACY_PATH.length());
        }
        return value;
    }

    /** 根据 Steam schema 中的完整 URL 或图标 hash 生成新版地址。 */
    public static String fromSteamValue(long appId, String iconValue) {
        if (!StringUtils.hasText(iconValue)) {
            return null;
        }
        String value = stripQuery(iconValue.trim());
        if (value.contains(CURRENT_PATH) || value.contains(LEGACY_PATH)) {
            return toCurrent(value);
        }
        int slashIndex = value.lastIndexOf('/');
        String fileName = slashIndex >= 0 ? value.substring(slashIndex + 1) : value;
        if (!StringUtils.hasText(fileName)) {
            return null;
        }
        if (!fileName.contains(".")) {
            fileName += ".jpg";
        }
        return SteamApiConstants.STEAM_ACHIEVEMENT_ICON_URL_PREFIX
                + appId + "/" + fileName;
    }

    /** 删除图标地址查询参数，便于比较和拼接稳定的当前地址。 */
    private static String stripQuery(String value) {
        int queryIndex = value.indexOf('?');
        return queryIndex >= 0 ? value.substring(0, queryIndex) : value;
    }
}
