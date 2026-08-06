package com.game.community.steam.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.community.common.constant.steam.SteamApiConstants;
import com.game.community.common.exception.BusinessException;
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

    public PlayerSummary getPlayerSummary(String steamId) {
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
            PlayerSummary summary = new PlayerSummary();
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

    public OwnedGamesResult getOwnedGames(String steamId) {
        requireApiKey();
        String url = UriComponentsBuilder.fromHttpUrl(SteamApiConstants.OWNED_GAMES_URL)
                .queryParam("key", steamProperties.getWebApiKey())
                .queryParam("steamid", steamId)
                .queryParam("include_appinfo", 1)
                .queryParam("include_played_free_games", 1)
                .toUriString();
        OwnedGamesResult result = new OwnedGamesResult();
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
                result.setLibraryPublic(result.getGameCount() > 0);
                return result;
            }
            for (JsonNode game : games) {
                OwnedGame ownedGame = new OwnedGame();
                long appId = game.path("appid").asLong();
                ownedGame.setAppId(appId);
                ownedGame.setName(game.path("name").asText(null));
                String iconHash = game.path("img_icon_url").asText(null);
                ownedGame.setIconUrl(buildIconUrl(appId, iconHash));
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

    public AchievementProgress getPlayerAchievements(String steamId, long appId) {
        List<PlayerAchievement> details = getPlayerAchievementDetails(steamId, appId);
        if (details.isEmpty()) {
            return null;
        }
        int unlocked = 0;
        for (PlayerAchievement item : details) {
            if (item.isUnlocked()) {
                unlocked++;
            }
        }
        AchievementProgress progress = new AchievementProgress();
        progress.setUnlocked(unlocked);
        progress.setTotal(details.size());
        return progress;
    }

    public List<PlayerAchievement> getPlayerAchievementDetails(String steamId, long appId) {
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
            List<PlayerAchievement> result = new ArrayList<>();
            for (JsonNode item : achievements) {
                String apiName = item.path("apiname").asText(null);
                if (!StringUtils.hasText(apiName)) {
                    continue;
                }
                PlayerAchievement achievement = new PlayerAchievement();
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

    public List<AchievementDefinition> getAchievementSchema(long appId) {
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
            List<AchievementDefinition> result = new ArrayList<>();
            for (JsonNode item : achievements) {
                String apiName = item.path("name").asText(null);
                if (!StringUtils.hasText(apiName)) {
                    continue;
                }
                String displayName = item.path("displayName").asText(null);
                if (!StringUtils.hasText(displayName)) {
                    displayName = apiName;
                }
                AchievementDefinition definition = new AchievementDefinition();
                definition.setApiName(apiName);
                definition.setName(displayName);
                definition.setDescription(item.path("description").asText(null));
                definition.setIconUrl(item.path("icon").asText(null));
                result.add(definition);
            }
            return result;
        } catch (Exception e) {
            log.debug("GetSchemaForGame 失败: appId={}", appId, e);
            return List.of();
        }
    }

    private String buildIconUrl(long appId, String iconHash) {
        if (!StringUtils.hasText(iconHash)) {
            return null;
        }
        return SteamApiConstants.STEAM_ICON_URL_PREFIX + appId + "/" + iconHash + ".jpg";
    }

    private void requireApiKey() {
        if (!StringUtils.hasText(steamProperties.getWebApiKey())) {
            throw new BusinessException("Steam API Key 未配置");
        }
    }

    @lombok.Data
    public static class PlayerSummary {
        private String steamId;
        private String personaName;
        private String avatarUrl;
        private String profileUrl;
    }

    @lombok.Data
    public static class OwnedGamesResult {
        private int gameCount;
        private boolean libraryPublic;
        private List<OwnedGame> games;
    }

    @lombok.Data
    public static class OwnedGame {
        private long appId;
        private String name;
        private String iconUrl;
        private int playtimeForever;
        private int playtimeTwoWeeks;
        private long lastPlayedEpoch;
    }

    @lombok.Data
    public static class AchievementProgress {
        private int unlocked;
        private int total;
    }

    @lombok.Data
    public static class PlayerAchievement {
        private String apiName;
        private boolean unlocked;
        private long unlockEpoch;
    }

    @lombok.Data
    public static class AchievementDefinition {
        private String apiName;
        private String name;
        private String description;
        private String iconUrl;
    }
}
