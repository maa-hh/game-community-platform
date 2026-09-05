package com.game.community.steam.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.community.common.constant.ApiErrorCodes;
import com.game.community.common.constant.steam.SteamApiConstants;
import com.game.community.common.exception.BusinessException;
import com.game.community.model.payload.steam.SteamAchievementDefinitionPayload;
import com.game.community.model.payload.steam.SteamGameBasicPayload;
import com.game.community.model.payload.steam.SteamGameDetailsPayload;
import com.game.community.model.payload.steam.SteamMoviePayload;
import com.game.community.model.payload.steam.SteamPricePayload;
import com.game.community.model.payload.steam.SteamScreenshotPayload;
import com.game.community.model.payload.steam.SteamReviewSummaryPayload;
import com.game.community.steam.config.SteamProperties;
import com.game.community.steam.util.SteamAchievementIconUrl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * Steam 游戏资料客户端。
 *
 * <p>只负责 appdetails 和价格接口；评价、搜索、榜单分别由对应客户端负责。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SteamStoreClient {

    private final RestTemplate restTemplate;
    private final SteamProperties steamProperties;
    private final ObjectMapper objectMapper;
    private final SteamReviewClient steamReviewClient;

    /** 获取游戏基础信息，并分别保存中文名和英文名。 */
    public SteamGameBasicPayload fetchBasicAppInfo(long appId) {
        SteamGameBasicPayload chinese = fetchBasicAppInfoByLanguageWithFallback(
                appId, SteamApiConstants.LIBRARY_NAME_ZH_LANGUAGE);
        SteamGameBasicPayload english = fetchBasicAppInfoByLanguage(
                appId, SteamApiConstants.LIBRARY_NAME_EN_LANGUAGE);
        chinese.setNameZh(chinese.getDisplayName());
        chinese.setNameEn(english.getDisplayName());
        chinese.setSteamName(StringUtils.hasText(chinese.getNameEn())
                ? chinese.getNameEn() : chinese.getNameZh());
        chinese.setDisplayName(StringUtils.hasText(chinese.getNameZh())
                ? chinese.getNameZh() : chinese.getNameEn());
        return chinese;
    }

    /** 中文基础信息没有名称时回退到 all，避免把空值写入游戏目录。 */
    private SteamGameBasicPayload fetchBasicAppInfoByLanguageWithFallback(long appId, String language) {
        try {
            SteamGameBasicPayload primary = fetchBasicAppInfoByLanguage(appId, language);
            if (StringUtils.hasText(primary.getDisplayName())) {
                return primary;
            }
        } catch (BusinessException e) {
            log.warn("Steam 中文基础信息为空，准备回退 all: appId={}", appId);
        }
        return fetchBasicAppInfoByLanguage(appId, SteamApiConstants.REVIEW_ALL_LANGUAGE);
    }

    /** 按指定语言获取 Steam 游戏基础字段。 */
    private SteamGameBasicPayload fetchBasicAppInfoByLanguage(long appId, String language) {
        JsonNode data = fetchAppData(appId, language);
        SteamGameBasicPayload result = new SteamGameBasicPayload();
        result.setAppId(appId);
        result.setSteamName(data.path("name").asText(null));
        result.setDisplayName(data.path("name").asText(null));
        result.setHeaderImage(data.path("header_image").asText(null));
        result.setDevelopers(readStringList(data.path("developers")));
        result.setPublishers(readStringList(data.path("publishers")));
        result.setGenres(readDescriptionList(data.path("genres")));
        result.setReleaseDate(data.path("release_date").path("date").asText(null));
        result.setSteamUrl(SteamApiConstants.STEAM_STORE_APP_URL_PREFIX + appId);
        result.setSteamIsFree(data.path("is_free").asBoolean(false));
        return result;
    }

    /** 获取游戏完整资料、价格、评价摘要、媒体和成就定义。 */
    public SteamGameDetailsPayload fetchAppDetails(long appId) {
        JsonNode data = fetchDetailDataWithFallback(appId);
        SteamGameDetailsPayload result = new SteamGameDetailsPayload();
        result.setAppId(appId);
        result.setSteamName(data.path("name").asText(null));
        result.setDisplayName(data.path("name").asText(null));
        result.setNameZh(result.getDisplayName());
        result.setShortDescription(data.path("short_description").asText(null));
        result.setAboutHtml(data.path("about_the_game").asText(null));
        result.setHeaderImage(data.path("header_image").asText(null));
        result.setDevelopers(readStringList(data.path("developers")));
        result.setPublishers(readStringList(data.path("publishers")));
        result.setGenres(readDescriptionList(data.path("genres")));
        result.setReleaseDate(data.path("release_date").path("date").asText(null));
        result.setSteamUrl(SteamApiConstants.STEAM_STORE_APP_URL_PREFIX + appId);

        SteamReviewSummaryPayload review = steamReviewClient.fetchReviewSummary(appId);
        if (review != null) {
            result.setSteamReviewScore(review.getPositivePercent());
            result.setSteamReviewCount(review.getTotalReviews());
        }
        applyDetailFields(result, data);
        return result;
    }

    /** 详情优先使用中文，中文返回无有效内容时回退 all。 */
    private JsonNode fetchDetailDataWithFallback(long appId) {
        try {
            JsonNode chinese = fetchAppData(appId, SteamApiConstants.LIBRARY_NAME_ZH_LANGUAGE);
            if (hasDetailContent(chinese)) {
                return chinese;
            }
        } catch (BusinessException e) {
            log.warn("Steam 中文游戏详情获取失败，准备回退 all: appId={}", appId);
        }
        return fetchAppData(appId, SteamApiConstants.REVIEW_ALL_LANGUAGE);
    }

    /** 判断 Steam 详情是否至少包含名称或介绍。 */
    private boolean hasDetailContent(JsonNode data) {
        return StringUtils.hasText(data.path("name").asText(null))
                || StringUtils.hasText(data.path("short_description").asText(null))
                || StringUtils.hasText(data.path("about_the_game").asText(null));
    }

    /** 请求 Steam appdetails 并返回指定游戏数据节点。 */
    private JsonNode fetchAppData(long appId, String language) {
        String url = UriComponentsBuilder.fromHttpUrl(SteamApiConstants.APP_DETAILS_URL)
                .queryParam("appids", appId)
                .queryParam("l", language)
                .queryParam("cc", steamProperties.getApiCc())
                .toUriString();
        try {
            JsonNode app = objectMapper.readTree(restTemplate.getForObject(url, String.class))
                    .path(String.valueOf(appId));
            if (!app.path("success").asBoolean(false)) {
                throw new BusinessException(ApiErrorCodes.NOT_FOUND, "Steam 商店未找到该游戏");
            }
            return app.path("data");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Steam 游戏资料获取失败: appId={}, language={}", appId, language, e);
            throw new BusinessException("获取 Steam 游戏资料失败");
        }
    }

    /** 获取价格动态字段，不请求游戏介绍、媒体和成就。 */
    public SteamPricePayload fetchPrice(long appId) {
        JsonNode data = fetchAppData(appId, steamProperties.getApiLang());
        SteamPricePayload result = new SteamPricePayload();
        result.setFree(data.path("is_free").asBoolean(false));
        JsonNode overview = data.path("price_overview");
        if (overview.isMissingNode() || overview.isNull()) {
            result.setCurrency("CNY");
            result.setInitial(0);
            result.setFinalPrice(0);
            result.setDiscountPercent(0);
            result.setFormatted(result.getFree() ? "免费" : "暂无价格");
            return result;
        }
        result.setCurrency(overview.path("currency").asText("CNY"));
        result.setInitial(overview.path("initial").asInt(0));
        result.setFinalPrice(overview.path("final").asInt(0));
        result.setDiscountPercent(overview.path("discount_percent").asInt(0));
        long discountEndAt = overview.path("discount_expiration").asLong(0L);
        result.setDiscountEndAt(discountEndAt > 0 ? discountEndAt : null);
        result.setFormatted(overview.path("final_formatted").asText(null));
        return result;
    }

    /** 将 appdetails 中的媒体、价格、评分和成就字段写入详情 payload。 */
    private void applyDetailFields(SteamGameDetailsPayload target, JsonNode data) {
        target.setScreenshots(readScreenshots(data.path("screenshots")));
        target.setMovies(readMovies(data.path("movies")));
        target.setCategories(readDescriptionList(data.path("categories")));
        target.setSteamIsFree(data.path("is_free").asBoolean(false));
        applyPriceFields(target, data.path("price_overview"));
        applyMetacriticFields(target, data.path("metacritic"));
        applyAchievementFields(target, data.path("achievements"));
        applyPcRequirements(target, data.path("pc_requirements"));
    }

    /** 解析 appdetails 价格字段。 */
    private void applyPriceFields(SteamGameDetailsPayload target, JsonNode price) {
        if (price.isMissingNode() || price.isNull()) {
            target.setPriceCurrency("CNY");
            target.setPriceInitial(0);
            target.setPriceFinal(0);
            target.setPriceDiscount(0);
            target.setPriceFormatted(Boolean.TRUE.equals(target.getSteamIsFree()) ? "免费" : "暂无价格");
            return;
        }
        target.setPriceCurrency(price.path("currency").asText("CNY"));
        target.setPriceInitial(price.path("initial").asInt(0));
        target.setPriceFinal(price.path("final").asInt(0));
        target.setPriceDiscount(price.path("discount_percent").asInt(0));
        long discountEndAt = price.path("discount_expiration").asLong(0L);
        target.setPriceDiscountEndAt(discountEndAt > 0 ? discountEndAt : null);
        String formatted = price.path("final_formatted").asText(null);
        target.setPriceFormatted(StringUtils.hasText(formatted) ? formatted : "暂无价格");
    }

    /** 解析 Metacritic 评分。 */
    private void applyMetacriticFields(SteamGameDetailsPayload target, JsonNode metacritic) {
        if (metacritic.isMissingNode() || metacritic.isNull()) {
            return;
        }
        int score = metacritic.path("score").asInt(0);
        if (score > 0) {
            target.setMetacriticScore(score);
        }
        target.setMetacriticUrl(metacritic.path("url").asText(null));
    }

    /** 解析成就总数，并使用 Steam 成就 schema 作为成就定义来源。 */
    private void applyAchievementFields(SteamGameDetailsPayload target, JsonNode achievements) {
        int total = achievements.path("total").asInt(0);
        if (total > 0) {
            target.setAchievementTotal(total);
        }
        List<SteamAchievementDefinitionPayload> definitions = fetchAchievementSchema(
                target.getAppId(), achievements);
        if (definitions.isEmpty()) {
            return;
        }
        target.setAchievementHighlights(definitions);
        if (target.getAchievementTotal() == null
                || target.getAchievementTotal() < definitions.size()) {
            target.setAchievementTotal(definitions.size());
        }
    }

    /** 获取 Steam 官方成就 schema。 */
    private List<SteamAchievementDefinitionPayload> fetchAchievementSchema(
            long appId, JsonNode storeAchievements) {
        if (StringUtils.hasText(steamProperties.getWebApiKey())) {
            String url = UriComponentsBuilder.fromHttpUrl(SteamApiConstants.GAME_SCHEMA_URL)
                    .queryParam("key", steamProperties.getWebApiKey())
                    .queryParam("appid", appId)
                    .queryParam("l", steamProperties.getApiLang())
                    .toUriString();
            try {
                JsonNode achievements = objectMapper.readTree(restTemplate.getForObject(url, String.class))
                        .path("game").path("availableGameStats").path("achievements");
                if (achievements.isArray()) {
                    List<SteamAchievementDefinitionPayload> result = new ArrayList<>();
                    for (JsonNode item : achievements) {
                        String apiName = item.path("name").asText(null);
                        String name = item.path("displayName").asText(null);
                        if (!StringUtils.hasText(apiName) || !StringUtils.hasText(name)) {
                            continue;
                        }
                        SteamAchievementDefinitionPayload definition = new SteamAchievementDefinitionPayload();
                        definition.setApiName(apiName);
                        definition.setName(name);
                        definition.setDescription(item.path("description").asText(null));
                        definition.setIconUrl(SteamAchievementIconUrl.fromSteamValue(
                                appId, item.path("icon").asText(null)));
                        result.add(definition);
                    }
                    if (!result.isEmpty()) {
                        return result;
                    }
                }
            } catch (Exception e) {
                log.warn("Steam 成就 schema 获取失败: appId={}", appId, e);
            }
        }

        return readStoreAchievementHighlights(appId, storeAchievements);
    }

    /** 无 Web API Key 或 schema 不可用时，使用商店详情中的高亮成就。 */
    private List<SteamAchievementDefinitionPayload> readStoreAchievementHighlights(
            long appId, JsonNode storeAchievements) {
        JsonNode highlighted = storeAchievements.path("highlighted");
        if (!highlighted.isArray()) {
            return List.of();
        }
        List<SteamAchievementDefinitionPayload> result = new ArrayList<>();
        for (JsonNode item : highlighted) {
            String iconUrl = item.path("path").asText(null);
            if (!StringUtils.hasText(iconUrl)) {
                iconUrl = SteamAchievementIconUrl.fromSteamValue(
                        appId, item.path("icon").asText(null));
            }
            String name = item.path("localized_name").asText(null);
            if (!StringUtils.hasText(name)) {
                name = item.path("name").asText(null);
            }
            if (!StringUtils.hasText(name) && !StringUtils.hasText(iconUrl)) {
                continue;
            }
            SteamAchievementDefinitionPayload definition = new SteamAchievementDefinitionPayload();
            definition.setApiName(item.path("name").asText(iconUrl));
            definition.setName(StringUtils.hasText(name) ? name : "Steam 成就");
            definition.setIconUrl(SteamAchievementIconUrl.toCurrent(iconUrl));
            result.add(definition);
        }
        return result;
    }

    /** 解析 PC 最低和推荐配置。 */
    private void applyPcRequirements(SteamGameDetailsPayload target, JsonNode requirements) {
        if (requirements.isMissingNode() || requirements.isNull()) {
            return;
        }
        target.setPcRequirementsMin(requirements.path("minimum").asText(null));
        target.setPcRequirementsRec(requirements.path("recommended").asText(null));
    }

    /** 解析截图列表。 */
    private List<SteamScreenshotPayload> readScreenshots(JsonNode screenshots) {
        if (!screenshots.isArray()) {
            return null;
        }
        List<SteamScreenshotPayload> result = new ArrayList<>();
        for (JsonNode item : screenshots) {
            String fullUrl = item.path("path_full").asText(null);
            if (!StringUtils.hasText(fullUrl)) {
                continue;
            }
            SteamScreenshotPayload screenshot = new SteamScreenshotPayload();
            screenshot.setFullUrl(fullUrl);
            screenshot.setThumbnailUrl(item.path("path_thumbnail").asText(fullUrl));
            result.add(screenshot);
        }
        return result.isEmpty() ? null : result;
    }

    /** 解析视频列表，优先使用 Steam 返回的最高画质 mp4/webm 地址。 */
    private List<SteamMoviePayload> readMovies(JsonNode movies) {
        if (!movies.isArray()) {
            return null;
        }
        List<SteamMoviePayload> result = new ArrayList<>();
        for (JsonNode item : movies) {
            String mp4Url = item.path("mp4").path("max").asText(null);
            String webmUrl = item.path("webm").path("max").asText(null);
            if (!StringUtils.hasText(mp4Url) && !StringUtils.hasText(webmUrl)) {
                continue;
            }
            SteamMoviePayload movie = new SteamMoviePayload();
            movie.setName(item.path("name").asText("预告片"));
            movie.setThumbnailUrl(item.path("thumbnail").asText(null));
            movie.setMp4Url(mp4Url);
            movie.setWebmUrl(webmUrl);
            result.add(movie);
        }
        return result.isEmpty() ? null : result;
    }

    /** 解析 Steam 返回的字符串数组。 */
    private List<String> readStringList(JsonNode values) {
        if (!values.isArray()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (JsonNode value : values) {
            if (value.isTextual() && StringUtils.hasText(value.asText())) {
                result.add(value.asText());
            }
        }
        return result;
    }

    /** 解析 Steam 返回的带 description 字段的分类数组。 */
    private List<String> readDescriptionList(JsonNode values) {
        if (!values.isArray()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (JsonNode value : values) {
            String description = value.path("description").asText(null);
            if (StringUtils.hasText(description)) {
                result.add(description);
            }
        }
        return result;
    }
}
