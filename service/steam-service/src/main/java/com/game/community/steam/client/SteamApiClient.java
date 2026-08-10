package com.game.community.steam.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.community.common.constant.steam.SteamApiConstants;
import com.game.community.common.exception.BusinessException;
import com.game.community.model.payload.steam.SteamAchievementDefinitionPayload;
import com.game.community.model.payload.steam.SteamOwnedGamePayload;
import com.game.community.model.payload.steam.SteamOwnedGamesPayload;
import com.game.community.model.payload.steam.SteamPlayerAchievementPayload;
import com.game.community.model.payload.steam.SteamPlayerSummaryPayload;
import com.game.community.steam.config.SteamProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class SteamApiClient {

    private final RestTemplate restTemplate;
    private final SteamProperties steamProperties;
    private final ObjectMapper objectMapper;

    /** 获取 Steam 用户公开资料。 */
    public SteamPlayerSummaryPayload getPlayerSummary(String steamId) {
        requireApiKey();
        String url = UriComponentsBuilder.fromHttpUrl(SteamApiConstants.PLAYER_SUMMARIES_URL)
                .queryParam("key", steamProperties.getWebApiKey())
                .queryParam("steamids", steamId)
                .toUriString();
        try {
            String body = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(body);
            JsonNode players = root.path("response").path("players");
            if (!players.isArray() || players.isEmpty()) {
                throw new BusinessException("未找到 Steam 用户资料");
            }
            JsonNode player = players.get(0);
            SteamPlayerSummaryPayload summary = new SteamPlayerSummaryPayload();
            summary.setSteamId(player.path("steamid").asText());
            summary.setPersonaName(player.path("personaname").asText(null));
            summary.setAvatarUrl(player.path("avatarfull").asText(null));
            summary.setProfileUrl(player.path("profileurl").asText(null));
            return summary;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("GetPlayerSummaries 失败: steamId={}", steamId, e);
            throw new BusinessException("获取 Steam 用户资料失败");
        }
    }

    /** 获取 Steam 等级，接口失败时返回 0。 */
    public int getSteamLevel(String steamId) {
        requireApiKey();
        String url = UriComponentsBuilder.fromHttpUrl(SteamApiConstants.STEAM_LEVEL_URL)
                .queryParam("key", steamProperties.getWebApiKey())
                .queryParam("steamid", steamId)
                .toUriString();
        try {
            String body = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(body);
            return root.path("response").path("player_level").asInt(0);
        } catch (Exception e) {
            log.warn("GetSteamLevel 失败: steamId={}", steamId, e);
            return 0;
        }
    }

    /** 获取用户拥有的游戏卡片数据，并分别补齐中文名和英文名。 */
    public SteamOwnedGamesPayload getOwnedGames(String steamId) {
        requireApiKey();
        SteamOwnedGamesPayload chineseResult = fetchOwnedGames(
                steamId, SteamApiConstants.LIBRARY_NAME_ZH_LANGUAGE);
        if (!chineseResult.isLibraryPublic()) {
            return chineseResult;
        }

        SteamOwnedGamesPayload englishResult = fetchOwnedGames(
                steamId, SteamApiConstants.LIBRARY_NAME_EN_LANGUAGE);
        mergeLibraryNames(chineseResult, englishResult);
        return chineseResult;
    }

    /** 按语言拉取 Steam 游戏库，只解析游戏卡片需要的基础字段。 */
    private SteamOwnedGamesPayload fetchOwnedGames(String steamId, String language) {
        String url = UriComponentsBuilder.fromHttpUrl(SteamApiConstants.OWNED_GAMES_URL)
                .queryParam("key", steamProperties.getWebApiKey())
                .queryParam("steamid", steamId)
                .queryParam("include_appinfo", 1)
                .queryParam("include_played_free_games", 1)
                .queryParam("l", language)
                .toUriString();
        SteamOwnedGamesPayload result = new SteamOwnedGamesPayload();
        result.setGames(new ArrayList<>());
        try {
            String body = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(body);
            JsonNode response = root.path("response");
            if (response.has("game_count")) {
                result.setGameCount(response.path("game_count").asInt(0));
            }
            JsonNode games = response.path("games");
            if (!games.isArray()) {
                result.setLibraryPublic(response.has("game_count"));
                return result;
            }
            for (JsonNode game : games) {
                SteamOwnedGamePayload ownedGame = new SteamOwnedGamePayload();
                long appId = game.path("appid").asLong();
                ownedGame.setAppId(appId);
                ownedGame.setName(game.path("name").asText(null));
                String iconHash = game.path("img_icon_url").asText(null);
                ownedGame.setIconUrl(buildIconUrl(appId, iconHash));
                ownedGame.setCoverUrl(
                        SteamApiConstants.STEAM_HEADER_IMAGE_URL_PREFIX + appId + "/header.jpg");
                ownedGame.setPlaytimeForever(game.path("playtime_forever").asInt(0));
                ownedGame.setPlaytimeTwoWeeks(game.path("playtime_2weeks").asInt(0));
                ownedGame.setLastPlayedEpoch(game.path("rtime_last_played").asLong(0));
                result.getGames().add(ownedGame);
            }
            result.setLibraryPublic(true);
            if (result.getGameCount() == 0) {
                result.setGameCount(result.getGames().size());
            }
            return result;
        } catch (Exception e) {
            log.warn("GetOwnedGames 失败: steamId={}", steamId, e);
            result.setLibraryPublic(false);
            return result;
        }
    }

    /** 将中文请求结果和英文请求结果按 App ID 合并。 */
    private void mergeLibraryNames(
            SteamOwnedGamesPayload chineseResult,
            SteamOwnedGamesPayload englishResult) {
        Map<Long, SteamOwnedGamePayload> englishGames = new HashMap<>();
        if (englishResult != null && englishResult.getGames() != null) {
            for (SteamOwnedGamePayload game : englishResult.getGames()) {
                englishGames.put(game.getAppId(), game);
            }
        }
        for (SteamOwnedGamePayload game : chineseResult.getGames()) {
            game.setNameZh(game.getName());
            SteamOwnedGamePayload englishGame = englishGames.get(game.getAppId());
            game.setNameEn(englishGame == null ? null : englishGame.getName());
            game.setName(resolveDisplayName(game.getNameZh(), game.getNameEn()));
        }
    }

    /** 按中文、英文、原始名称顺序选择游戏卡片展示名称。 */
    private String resolveDisplayName(String nameZh, String nameEn) {
        if (StringUtils.hasText(nameZh)) {
            return nameZh;
        }
        return StringUtils.hasText(nameEn) ? nameEn : null;
    }

    /** 获取用户指定游戏的逐项成就解锁状态。 */
    public List<SteamPlayerAchievementPayload> getPlayerAchievementDetails(String steamId, long appId) {
        if (!StringUtils.hasText(steamProperties.getWebApiKey())) {
            return List.of();
        }
        String url = UriComponentsBuilder.fromHttpUrl(SteamApiConstants.PLAYER_ACHIEVEMENTS_URL)
                .queryParam("key", steamProperties.getWebApiKey())
                .queryParam("steamid", steamId)
                .queryParam("appid", appId)
                .queryParam("l", steamProperties.getApiLang())
                .toUriString();
        try {
            String body = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(body);
            JsonNode playerStats = root.path("playerstats");
            if (!playerStats.path("success").asBoolean(false)) {
                return List.of();
            }
            JsonNode achievements = playerStats.path("achievements");
            if (!achievements.isArray() || achievements.isEmpty()) {
                return List.of();
            }
            List<SteamPlayerAchievementPayload> result = new ArrayList<>();
            for (JsonNode item : achievements) {
                String apiName = item.path("apiname").asText(null);
                if (!StringUtils.hasText(apiName)) {
                    continue;
                }
                SteamPlayerAchievementPayload achievement = new SteamPlayerAchievementPayload();
                achievement.setApiName(apiName);
                achievement.setUnlocked(item.path("achieved").asInt(0) == 1);
                achievement.setUnlockEpoch(item.path("unlocktime").asLong(0));
                result.add(achievement);
            }
            return result;
        } catch (Exception e) {
            log.debug("GetPlayerAchievements 失败: steamId={}, appId={}", steamId, appId);
            return List.of();
        }
    }

    /** 获取指定游戏的全球成就解锁比例。 */
    public Map<String, Double> getGlobalAchievementPercentages(long appId) {
        if (!StringUtils.hasText(steamProperties.getWebApiKey())) {
            return Map.of();
        }
        String url = UriComponentsBuilder.fromHttpUrl(SteamApiConstants.GLOBAL_ACHIEVEMENTS_URL)
                .queryParam("gameid", appId)
                .toUriString();
        try {
            String body = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(body);
            JsonNode achievements = root.path("achievementpercentages").path("achievements");
            if (!achievements.isArray()) {
                return Map.of();
            }
            Map<String, Double> result = new HashMap<>();
            for (JsonNode item : achievements) {
                String apiName = item.path("name").asText(null);
                if (!StringUtils.hasText(apiName)) {
                    continue;
                }
                result.put(apiName, item.path("percent").asDouble(0));
            }
            return result;
        } catch (Exception e) {
            log.debug("GetGlobalAchievementPercentagesForApp 失败: appId={}", appId, e);
            return Map.of();
        }
    }

    /** 获取指定游戏的官方成就定义。 */
    public List<SteamAchievementDefinitionPayload> getAchievementSchema(long appId) {
        if (!StringUtils.hasText(steamProperties.getWebApiKey())) {
            return List.of();
        }
        String url = UriComponentsBuilder.fromHttpUrl(SteamApiConstants.GAME_SCHEMA_URL)
                .queryParam("key", steamProperties.getWebApiKey())
                .queryParam("appid", appId)
                .queryParam("l", steamProperties.getApiLang())
                .toUriString();
        try {
            String body = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(body);
            JsonNode achievements = root.path("game")
                    .path("availableGameStats")
                    .path("achievements");
            if (!achievements.isArray() || achievements.isEmpty()) {
                return List.of();
            }
            List<SteamAchievementDefinitionPayload> result = new ArrayList<>();
            for (JsonNode item : achievements) {
                String apiName = item.path("name").asText(null);
                if (!StringUtils.hasText(apiName)) {
                    continue;
                }
                String displayName = item.path("displayName").asText(null);
                if (!StringUtils.hasText(displayName)) {
                    displayName = apiName;
                }
                SteamAchievementDefinitionPayload definition = new SteamAchievementDefinitionPayload();
                definition.setApiName(apiName);
                definition.setName(displayName);
                definition.setDescription(item.path("description").asText(null));
                definition.setIconUrl(buildAchievementIconUrl(appId, item.path("icon").asText(null)));
                result.add(definition);
            }
            return result;
        } catch (Exception e) {
            log.debug("GetSchemaForGame 失败: appId={}", appId, e);
            return List.of();
        }
    }

    /** 根据 Steam 图标 hash 生成图标地址。 */
    private String buildIconUrl(long appId, String iconHash) {
        if (!StringUtils.hasText(iconHash)) {
            return null;
        }
        return SteamApiConstants.STEAM_ICON_URL_PREFIX + appId + "/" + iconHash + ".jpg";
    }

    /** 按早期游戏库图标链路，从 Steam 成就 URL 中提取 hash 重新生成地址。 */
    private String buildAchievementIconUrl(long appId, String iconUrl) {
        if (!StringUtils.hasText(iconUrl)) {
            return null;
        }
        String value = iconUrl.trim();
        int queryIndex = value.indexOf('?');
        if (queryIndex >= 0) {
            value = value.substring(0, queryIndex);
        }
        int slashIndex = value.lastIndexOf('/');
        String fileName = slashIndex >= 0 ? value.substring(slashIndex + 1) : value;
        if (fileName.endsWith(".jpg")) {
            fileName = fileName.substring(0, fileName.length() - 4);
        }
        return StringUtils.hasText(fileName) ? buildIconUrl(appId, fileName) : null;
    }

    /** 校验调用 Steam Web API 所需的 API Key。 */
    private void requireApiKey() {
        if (!StringUtils.hasText(steamProperties.getWebApiKey())) {
            throw new BusinessException("Steam API Key 未配置");
        }
    }

}
