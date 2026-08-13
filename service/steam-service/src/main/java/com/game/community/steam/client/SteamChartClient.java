package com.game.community.steam.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.community.common.constant.steam.GameBoardConstants;
import com.game.community.common.constant.steam.SteamApiConstants;
import com.game.community.model.payload.steam.SteamChartGamePayload;
import com.game.community.model.payload.steam.SteamPricePayload;
import com.game.community.steam.config.SteamProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Steam 搜索和榜单接口客户端。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SteamChartClient {

    private static final Pattern APP_ID_IN_ASSET_URL =
            Pattern.compile("/apps/(\\d+)(?:/|$)");

    private final RestTemplate restTemplate;
    private final SteamProperties steamProperties;
    private final ObjectMapper objectMapper;

    /** 按搜索关键词分页拉取轻量游戏卡片。 */
    public List<SteamChartGamePayload> searchApps(String keyword, int start, int limit) {
        int safeStart = Math.max(0, start);
        int safeLimit = Math.max(1, Math.min(limit, 50));
        String url = UriComponentsBuilder.fromHttpUrl(SteamApiConstants.STORE_SEARCH_URL)
                .queryParam("term", keyword)
                .queryParam("start", safeStart)
                .queryParam("count", safeLimit)
                .queryParam("json", 1)
                .queryParam("category1", 998)
                .queryParam("cc", steamProperties.getApiCc())
                .queryParam("l", steamProperties.getApiLang())
                .toUriString();
        try {
            JsonNode items = objectMapper.readTree(restTemplate.getForObject(url, String.class))
                    .path("items");
            if (!items.isArray()) {
                return List.of();
            }
            List<SteamChartGamePayload> result = new ArrayList<>();
            for (JsonNode item : items) {
                long appId = resolveAppId(item);
                if (appId <= 0) {
                    continue;
                }
                result.add(toChartGame(item, appId));
                if (result.size() >= safeLimit) {
                    break;
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("Steam Store 搜索失败: keyword={}", keyword, e);
            return List.of();
        }
    }

    /** 按榜单和偏移量分页拉取轻量游戏卡片。 */
    public List<SteamChartGamePayload> fetchChartGames(String board, int start, int limit) {
        int safeStart = Math.max(0, start);
        int safeLimit = Math.max(1, Math.min(limit, SteamApiConstants.CHART_REQUEST_PAGE_SIZE));
        if (safeStart > 0 || GameBoardConstants.FREE.equalsIgnoreCase(board)) {
            return fetchSearchChartGames(board, safeStart, safeLimit);
        }

        List<SteamChartGamePayload> featured = extractFeaturedGames(
                loadFeaturedCategories(), resolveFeaturedSection(board), safeLimit);
        if (featured.size() >= safeLimit) {
            return featured;
        }
        return mergeDistinct(featured, fetchSearchChartGames(board, 0, safeLimit), safeLimit);
    }

    /** 拉取 Steam 精选榜单数据。 */
    private JsonNode loadFeaturedCategories() {
        String url = UriComponentsBuilder.fromHttpUrl(SteamApiConstants.FEATURED_CATEGORIES_URL)
                .queryParam("cc", steamProperties.getApiCc())
                // 榜单展示固定使用中文，不受通用 Steam 语言配置影响。
                .queryParam("l", SteamApiConstants.LIBRARY_NAME_ZH_LANGUAGE)
                .toUriString();
        try {
            return objectMapper.readTree(restTemplate.getForObject(url, String.class));
        } catch (Exception e) {
            log.warn("Steam 精选榜单获取失败", e);
            return objectMapper.createObjectNode();
        }
    }

    /** 将榜单类型映射为 Steam 精选分类。 */
    private String resolveFeaturedSection(String board) {
        if (GameBoardConstants.HOT.equalsIgnoreCase(board)) {
            return GameBoardConstants.FEATURED_SECTION_TOP_SELLERS;
        }
        if (GameBoardConstants.NEW.equalsIgnoreCase(board)) {
            return GameBoardConstants.FEATURED_SECTION_NEW_RELEASES;
        }
        if (GameBoardConstants.DISCOUNT.equalsIgnoreCase(board)) {
            return GameBoardConstants.FEATURED_SECTION_SPECIALS;
        }
        return null;
    }

    /** 从单个精选分类提取游戏卡片。 */
    private List<SteamChartGamePayload> extractFeaturedGames(
            JsonNode root, String section, int limit) {
        if (!StringUtils.hasText(section)) {
            return List.of();
        }
        JsonNode items = root.path(section).path("items");
        if (!items.isArray()) {
            return List.of();
        }
        List<SteamChartGamePayload> result = new ArrayList<>();
        Set<Long> appIds = new LinkedHashSet<>();
        for (JsonNode item : items) {
            long appId = resolveAppId(item);
            if (appId <= 0 || !appIds.add(appId)) {
                continue;
            }
            result.add(toChartGame(item, appId));
            if (result.size() >= limit) {
                break;
            }
        }
        return result;
    }

    /** 调用 Steam 搜索接口获取指定类型榜单。 */
    private List<SteamChartGamePayload> fetchSearchChartGames(
            String board, int start, int limit) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl(SteamApiConstants.STORE_SEARCH_URL)
                .queryParam("json", 1)
                .queryParam("start", start)
                .queryParam("count", limit)
                .queryParam("category1", 998)
                .queryParam("cc", steamProperties.getApiCc())
                // 榜单展示固定使用中文，不受通用 Steam 语言配置影响。
                .queryParam("l", SteamApiConstants.LIBRARY_NAME_ZH_LANGUAGE);
        if (GameBoardConstants.FREE.equalsIgnoreCase(board)) {
            builder.queryParam("maxprice", "free");
        } else if (GameBoardConstants.HOT.equalsIgnoreCase(board)) {
            builder.queryParam("filter", "topsellers");
        } else if (GameBoardConstants.NEW.equalsIgnoreCase(board)) {
            builder.queryParam("filter", "popularnew");
        } else if (GameBoardConstants.DISCOUNT.equalsIgnoreCase(board)) {
            builder.queryParam("specials", 1);
        } else {
            return List.of();
        }
        try {
            JsonNode items = objectMapper.readTree(
                    restTemplate.getForObject(builder.toUriString(), String.class)).path("items");
            if (!items.isArray()) {
                return List.of();
            }
            List<SteamChartGamePayload> result = new ArrayList<>();
            Set<Long> appIds = new LinkedHashSet<>();
            for (JsonNode item : items) {
                long appId = resolveAppId(item);
                if (appId <= 0 || !appIds.add(appId)) {
                    continue;
                }
                result.add(toChartGame(item, appId));
                if (result.size() >= limit) {
                    break;
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("Steam 榜单搜索失败: board={}", board, e);
            return List.of();
        }
    }

    /** 合并精选榜单和搜索榜单，并按 App ID 去重。 */
    private List<SteamChartGamePayload> mergeDistinct(
            List<SteamChartGamePayload> primary,
            List<SteamChartGamePayload> extra,
            int limit) {
        Set<Long> appIds = new LinkedHashSet<>();
        List<SteamChartGamePayload> result = new ArrayList<>();
        for (SteamChartGamePayload game : primary) {
            if (game != null && game.getAppId() != null && appIds.add(game.getAppId())) {
                result.add(game);
            }
        }
        for (SteamChartGamePayload game : extra) {
            if (game != null && game.getAppId() != null && appIds.add(game.getAppId())) {
                result.add(game);
            }
            if (result.size() >= limit) {
                break;
            }
        }
        return result;
    }

    /** 将 Steam 当前榜单字段转换为轻量游戏 payload。 */
    private SteamChartGamePayload toChartGame(JsonNode item, long appId) {
        SteamChartGamePayload game = new SteamChartGamePayload();
        game.setAppId(appId);
        game.setName(item.path("name").asText("游戏 " + appId));
        game.setCoverUrl(resolveCoverUrl(item));
        JsonNode price = item.path("price");
        if (price.isMissingNode() || price.isNull()) {
            return game;
        }
        SteamPricePayload pricePayload = new SteamPricePayload();
        pricePayload.setFree(price.path("final_price").asInt(0) == 0);
        pricePayload.setCurrency(price.path("currency").asText(null));
        pricePayload.setInitial(price.path("initial_price").asInt(0));
        pricePayload.setFinalPrice(price.path("final_price").asInt(0));
        pricePayload.setDiscountPercent(price.path("discount_percent").asInt(0));
        pricePayload.setDiscountEndAt(price.path("discount_expiration").asLong(0L));
        pricePayload.setFormatted(price.path("final_formatted").asText(null));
        game.setPrice(pricePayload);
        return game;
    }

    /** 兼容精选榜单的 id/appid 和搜索结果图片 URL 中的 AppID。 */
    private long resolveAppId(JsonNode item) {
        long appId = item.path("id").asLong(0L);
        if (appId <= 0) {
            appId = item.path("appid").asLong(0L);
        }
        if (appId > 0) {
            return appId;
        }
        for (String field : List.of("header_image", "logo", "icon")) {
            String assetUrl = item.path(field).asText(null);
            Matcher matcher = assetUrl == null ? null : APP_ID_IN_ASSET_URL.matcher(assetUrl);
            if (matcher != null && matcher.find()) {
                try {
                    return Long.parseLong(matcher.group(1));
                } catch (NumberFormatException ignored) {
                    // 继续尝试其他图片字段。
                }
            }
        }
        return 0L;
    }

    /** 兼容精选接口的 header_image 和搜索接口的 logo。 */
    private String resolveCoverUrl(JsonNode item) {
        String headerImage = item.path("header_image").asText(null);
        if (StringUtils.hasText(headerImage)) {
            return headerImage;
        }
        return item.path("logo").asText(null);
    }
}
