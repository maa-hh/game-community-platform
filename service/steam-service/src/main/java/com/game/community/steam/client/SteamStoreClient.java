package com.game.community.steam.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.game.community.common.constant.steam.GameBoardConstants;
import com.game.community.common.constant.steam.GameCatalogConstants;
import com.game.community.common.constant.steam.SteamApiConstants;
import com.game.community.common.exception.BusinessException;
import com.game.community.model.entity.game.GameCatalog;
import com.game.community.model.vo.game.GameAchievementVO;
import com.game.community.model.vo.game.GameMovieVO;
import com.game.community.model.vo.game.GameScreenshotVO;
import com.game.community.model.vo.game.GameListItemVO;
import com.game.community.model.vo.game.GamePriceVO;
import com.game.community.steam.config.SteamProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class SteamStoreClient {

    private static final Pattern POSITIVE_REVIEW_COUNT = Pattern.compile(
            "id\\s*=\\s*[\\\"']review_summary_num_positive_reviews[\\\"'][^>]*value\\s*=\\s*[\\\"'](\\d+)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TOTAL_REVIEW_COUNT = Pattern.compile(
            "id\\s*=\\s*[\\\"']review_summary_num_reviews[\\\"'][^>]*value\\s*=\\s*[\\\"'](\\d+)",
            Pattern.CASE_INSENSITIVE);

    private final RestTemplate restTemplate;
    private final SteamProperties steamProperties;
    private final ObjectMapper objectMapper;

    /** 获取游戏公共基础信息，不请求评价、价格和富媒体详情。 */
    public GameCatalog fetchBasicAppInfo(long appId) {
        GameCatalog chinese = fetchBasicAppInfo(appId, SteamApiConstants.LIBRARY_NAME_ZH_LANGUAGE);
        GameCatalog english;
        try {
            english = fetchBasicAppInfo(appId, SteamApiConstants.LIBRARY_NAME_EN_LANGUAGE);
        } catch (Exception e) {
            log.debug("Steam 英文基础信息获取失败，保留中文信息: appId={}", appId);
            english = new GameCatalog();
        }
        chinese.setNameZh(chinese.getDisplayName());
        chinese.setNameEn(english.getDisplayName());
        chinese.setSteamName(StringUtils.hasText(chinese.getNameEn())
                ? chinese.getNameEn() : chinese.getNameZh());
        chinese.setDisplayName(StringUtils.hasText(chinese.getNameZh())
                ? chinese.getNameZh() : chinese.getNameEn());
        return chinese;
    }

    /** 按语言获取游戏公共基础信息。 */
    private GameCatalog fetchBasicAppInfo(long appId, String language) {
        String url = UriComponentsBuilder.fromHttpUrl(SteamApiConstants.APP_DETAILS_URL)
                .queryParam("appids", appId)
                .queryParam("l", language)
                .toUriString();
        try {
            String body = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(body);
            JsonNode appNode = root.path(String.valueOf(appId));
            if (!appNode.path("success").asBoolean(false)) {
                throw new BusinessException("Steam 商店未找到该游戏");
            }
            JsonNode data = appNode.path("data");
            GameCatalog catalog = new GameCatalog();
            catalog.setAppId(appId);
            catalog.setSteamName(data.path("name").asText(null));
            catalog.setDisplayName(data.path("name").asText(null));
            catalog.setHeaderImage(data.path("header_image").asText(null));
            catalog.setDevelopers(readStringList(data.path("developers")));
            catalog.setPublishers(readStringList(data.path("publishers")));
            catalog.setGenres(readGenreList(data.path("genres")));
            catalog.setReleaseDate(data.path("release_date").path("date").asText(null));
            catalog.setSteamUrl(SteamApiConstants.STEAM_STORE_APP_URL_PREFIX + appId);
            catalog.setSteamIsFree(data.path("is_free").asBoolean(false));
            catalog.setStatus(1);
            catalog.setDetailReady(false);
            catalog.setRefreshStatus("BASIC_READY");
            LocalDateTime now = LocalDateTime.now();
            catalog.setSteamSyncedAt(now);
            catalog.setStaticSyncedAt(now);
            catalog.setLastRefreshAttemptAt(now);
            return catalog;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Steam 商店基础信息获取失败: appId={}", appId, e);
            throw new BusinessException("获取 Steam 游戏基础信息失败");
        }
    }

    public GameCatalog fetchAppDetails(long appId) {
        String url = UriComponentsBuilder.fromHttpUrl(SteamApiConstants.APP_DETAILS_URL)
                .queryParam("appids", appId)
                .queryParam("l", steamProperties.getApiLang())
                .queryParam("cc", steamProperties.getApiCc())
                .toUriString();
        try {
            String body = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(body);
            JsonNode appNode = root.path(String.valueOf(appId));
            if (!appNode.path("success").asBoolean(false)) {
                throw new BusinessException("Steam 商店未找到该游戏");
            }
            JsonNode data = appNode.path("data");
            GameCatalog catalog = new GameCatalog();
            catalog.setAppId(appId);
            catalog.setSteamName(data.path("name").asText(null));
            catalog.setDisplayName(data.path("name").asText(null));
            if (SteamApiConstants.LIBRARY_NAME_ZH_LANGUAGE.equals(steamProperties.getApiLang())) {
                catalog.setNameZh(catalog.getDisplayName());
            } else {
                catalog.setNameEn(catalog.getDisplayName());
            }
            catalog.setSteamShortDesc(data.path("short_description").asText(null));
            catalog.setSteamAboutHtml(data.path("about_the_game").asText(null));
            catalog.setHeaderImage(data.path("header_image").asText(null));
            catalog.setDevelopers(readStringList(data.path("developers")));
            catalog.setPublishers(readStringList(data.path("publishers")));
            catalog.setGenres(readGenreList(data.path("genres")));
            catalog.setReleaseDate(data.path("release_date").path("date").asText(null));
            catalog.setSteamUrl(SteamApiConstants.STEAM_STORE_APP_URL_PREFIX + appId);
            catalog.setDescSource(GameCatalogConstants.DESC_SOURCE_COMMUNITY_FIRST);
            catalog.setDiscussCount(0);
            catalog.setReviewCount(0);
            catalog.setStatus(1);
            LocalDateTime syncedAt = LocalDateTime.now();
            catalog.setSteamSyncedAt(syncedAt);
            catalog.setStaticSyncedAt(syncedAt);
            SteamReviewSummary reviewSummary = fetchReviewSummary(appId);
            if (reviewSummary != null) {
                catalog.setSteamReviewScore(reviewSummary.positivePercent());
                catalog.setSteamReviewCount(reviewSummary.totalReviews());
            }
            // 无评价的游戏也必须记录 metrics_synced_at，否则新详情插入会违反非空约束，
            // 并且会被榜单异步补齐任务反复判定为未完成。
            catalog.setMetricsSyncedAt(syncedAt);
            applyRichMediaFields(catalog, data);
            // 国区语言包对少数游戏只返回基础卡片字段，缺失的介绍/媒体/成就定义再用英文资料补齐。
            fillMissingRichFieldsFromEnglish(catalog, appId);
            applyDiscountEndAtFromStoreBrowse(catalog);
            catalog.setPriceSyncedAt(syncedAt);
            catalog.setRichSyncedAt(syncedAt);
            catalog.setLastRefreshAttemptAt(syncedAt);
            catalog.setDetailReady(true);
            catalog.setRefreshStatus("READY");
            catalog.setNextRefreshAt(syncedAt.plusDays(7));
            return catalog;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Steam Store appdetails 失败: appId={}", appId, e);
            throw new BusinessException("获取 Steam 游戏资料失败");
        }
    }

    /**
     * 从 Steam appreviews 接口拉取好评率（0–100）。
     * 无评价或请求失败时返回 null。
     */
    public Integer fetchReviewPositivePercent(long appId) {
        SteamReviewSummary summary = fetchReviewSummary(appId);
        return summary == null ? null : summary.positivePercent();
    }

    public SteamReviewSummary fetchReviewSummary(long appId) {
        String url = UriComponentsBuilder.fromHttpUrl(SteamApiConstants.APP_REVIEWS_URL + "/" + appId)
                .queryParam("json", 1)
                .queryParam("language", steamProperties.getApiLang())
                .queryParam("num_per_page", 0)
                .queryParam("filter", "summary")
                .toUriString();
        try {
            String body = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(body);
            if (!root.path("success").asBoolean(false)) {
                return null;
            }
            JsonNode summary = root.path("query_summary");
            int totalReviews = summary.path("total_reviews").asInt(0);
            int totalPositive = summary.path("total_positive").asInt(0);
            if (totalReviews <= 0) {
                return fetchReviewSummaryFromStorePage(appId);
            }
            Integer positivePercent =
                    Math.min(100, Math.max(0, Math.round(totalPositive * 100f / totalReviews)));
            return new SteamReviewSummary(positivePercent, totalReviews);
        } catch (Exception e) {
            log.warn("Steam appreviews 失败: appId={}", appId, e);
            return fetchReviewSummaryFromStorePage(appId);
        }
    }

    /**
     * appreviews 对部分新发行游戏会返回空汇总，但商店页已经有完整汇总。
     * 只读取商店页的两个隐藏汇总字段作为兜底，不抓取评论正文。
     */
    private SteamReviewSummary fetchReviewSummaryFromStorePage(long appId) {
        String url = SteamApiConstants.STEAM_STORE_APP_URL_PREFIX + appId
                + "/?l=" + steamProperties.getApiLang()
                + "&cc=" + steamProperties.getApiCc();
        try {
            String body = restTemplate.getForObject(url, String.class);
            if (!StringUtils.hasText(body)) {
                return null;
            }
            Integer positive = findNumber(POSITIVE_REVIEW_COUNT, body);
            Integer total = findNumber(TOTAL_REVIEW_COUNT, body);
            if (positive == null || total == null) {
                return null;
            }
            if (total <= 0) {
                return new SteamReviewSummary(0, 0);
            }
            return new SteamReviewSummary(
                    Math.min(100, Math.max(0, Math.round(positive * 100f / total))), total);
        } catch (Exception e) {
            log.debug("Steam 商店页评价汇总兜底失败: appId={}", appId, e);
            return null;
        }
    }

    private Integer findNumber(Pattern pattern, String body) {
        Matcher matcher = pattern.matcher(body);
        return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
    }

    /** 价格日更专用接口，只调用 appdetails，不拉取成就和媒体。 */
    public GamePriceVO fetchPrice(long appId) {
        String url = UriComponentsBuilder.fromHttpUrl(SteamApiConstants.APP_DETAILS_URL)
                .queryParam("appids", appId)
                .queryParam("l", steamProperties.getApiLang())
                .queryParam("cc", steamProperties.getApiCc())
                .toUriString();
        try {
            JsonNode data = objectMapper.readTree(restTemplate.getForObject(url, String.class))
                    .path(String.valueOf(appId)).path("data");
            if (data.isMissingNode() || !data.path("name").isTextual()) {
                return null;
            }
            GamePriceVO price = new GamePriceVO();
            price.setFree(data.path("is_free").asBoolean(false));
            price.setCurrency("CNY");
            price.setInitial(0);
            price.setFinalPrice(0);
            price.setDiscountPercent(0);
            JsonNode overview = data.path("price_overview");
            if (overview.isMissingNode() || overview.isNull()) {
                price.setFormatted(price.getFree() ? "免费" : "暂无价格");
                return price;
            }
            price.setCurrency(overview.path("currency").asText("CNY"));
            price.setInitial(overview.path("initial").asInt(0));
            price.setFinalPrice(overview.path("final").asInt(0));
            price.setDiscountPercent(overview.path("discount_percent").asInt(0));
            long endAt = overview.path("discount_expiration").asLong(0L);
            price.setDiscountEndAt(endAt > 0 ? endAt : null);
            price.setFormatted(overview.path("final_formatted").asText(null));
            return price;
        } catch (Exception e) {
            log.warn("Steam 价格更新失败: appId={}", appId, e);
            return null;
        }
    }

    /**
     * Steam Store 轻量搜索，只解析候选列表字段，不请求 appdetails、成就和媒体详情。
     */
    public List<GameListItemVO> searchApps(String keyword, int start, int limit) {
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
            String body = restTemplate.getForObject(url, String.class);
            JsonNode items = objectMapper.readTree(body).path("items");
            if (!items.isArray()) {
                return List.of();
            }
            List<GameListItemVO> result = new ArrayList<>();
            for (JsonNode item : items) {
                Long resolvedAppId = resolveSearchAppId(item);
                if (resolvedAppId == null || resolvedAppId <= 0) {
                    continue;
                }
                long appId = resolvedAppId;
                GameListItemVO vo = new GameListItemVO();
                vo.setAppId(appId);
                vo.setName(item.path("name").asText("游戏 " + appId));
                String cover = item.path("header_image").asText(null);
                if (!StringUtils.hasText(cover)) {
                    cover = item.path("logo").asText(null);
                }
                vo.setCoverUrl(cover);
                JsonNode price = item.path("price");
                if (!price.isMissingNode() && !price.isNull()) {
                    GamePriceVO priceVO = new GamePriceVO();
                    priceVO.setFree(price.path("final_price").asInt(-1) == 0);
                    priceVO.setCurrency(price.path("currency").asText(null));
                    priceVO.setInitial(price.path("initial_price").asInt(0));
                    priceVO.setFinalPrice(price.path("final_price").asInt(0));
                    priceVO.setDiscountPercent(price.path("discount_percent").asInt(0));
                    priceVO.setFormatted(price.path("final_formatted").asText(null));
                    vo.setPrice(priceVO);
                }
                result.add(vo);
                if (result.size() >= safeLimit) {
                    break;
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("Steam Store 轻量搜索失败: keyword={}", keyword, e);
            return List.of();
        }
    }

    public record SteamReviewSummary(Integer positivePercent, Integer totalReviews) {}

    private void applyRichMediaFields(GameCatalog catalog, JsonNode data) {
        catalog.setSteamScreenshots(readScreenshots(data.path("screenshots")));
        catalog.setSteamMovies(readMovies(data.path("movies")));
        catalog.setSteamCategories(readCategoryList(data.path("categories")));
        catalog.setSteamIsFree(data.path("is_free").asBoolean(false));
        applyPriceFields(catalog, data.path("price_overview"));
        applyMetacriticFields(catalog, data.path("metacritic"));
        applyAchievementFields(catalog, data.path("achievements"));
        applyPcRequirements(catalog, data.path("pc_requirements"));
    }

    private void fillMissingRichFieldsFromEnglish(GameCatalog catalog, long appId) {
        boolean missing = !StringUtils.hasText(catalog.getSteamShortDesc())
                || !StringUtils.hasText(catalog.getSteamAboutHtml())
                || catalog.getSteamScreenshots() == null
                || catalog.getSteamScreenshots().isEmpty()
                || catalog.getAchievementTotal() == null;
        if (!missing || SteamApiConstants.LIBRARY_NAME_EN_LANGUAGE.equalsIgnoreCase(
                steamProperties.getApiLang())) {
            return;
        }
        String url = UriComponentsBuilder.fromHttpUrl(SteamApiConstants.APP_DETAILS_URL)
                .queryParam("appids", appId)
                .queryParam("l", SteamApiConstants.LIBRARY_NAME_EN_LANGUAGE)
                .queryParam("cc", steamProperties.getApiCc())
                .toUriString();
        try {
            String body = restTemplate.getForObject(url, String.class);
            JsonNode data = objectMapper.readTree(body).path(String.valueOf(appId)).path("data");
            if (data.isMissingNode() || data.isNull()) {
                return;
            }
            if (!StringUtils.hasText(catalog.getSteamShortDesc())) {
                catalog.setSteamShortDesc(data.path("short_description").asText(null));
            }
            if (!StringUtils.hasText(catalog.getSteamAboutHtml())) {
                catalog.setSteamAboutHtml(data.path("about_the_game").asText(null));
            }
            if (catalog.getSteamScreenshots() == null || catalog.getSteamScreenshots().isEmpty()) {
                catalog.setSteamScreenshots(readScreenshots(data.path("screenshots")));
            }
            if (catalog.getSteamMovies() == null || catalog.getSteamMovies().isEmpty()) {
                catalog.setSteamMovies(readMovies(data.path("movies")));
            }
            if (catalog.getSteamCategories() == null || catalog.getSteamCategories().isEmpty()) {
                catalog.setSteamCategories(readCategoryList(data.path("categories")));
            }
            if (catalog.getAchievementTotal() == null) {
                applyAchievementFields(catalog, data.path("achievements"));
            }
            if (!StringUtils.hasText(catalog.getPcRequirementsMin())
                    && !StringUtils.hasText(catalog.getPcRequirementsRec())) {
                applyPcRequirements(catalog, data.path("pc_requirements"));
            }
        } catch (Exception e) {
            log.debug("Steam 英文资料补全失败: appId={}", appId, e);
        }
    }

    private void applyPriceFields(GameCatalog catalog, JsonNode priceNode) {
        if (priceNode.isMissingNode() || priceNode.isNull()) {
            catalog.setPriceCurrency("CNY");
            catalog.setPriceInitial(0);
            catalog.setPriceFinal(0);
            catalog.setPriceDiscount(0);
            catalog.setPriceFormatted(Boolean.TRUE.equals(catalog.getSteamIsFree()) ? "免费" : "暂无价格");
            return;
        }
        catalog.setPriceCurrency(priceNode.path("currency").asText("CNY"));
        catalog.setPriceInitial(priceNode.path("initial").asInt(0));
        catalog.setPriceFinal(priceNode.path("final").asInt(0));
        catalog.setPriceDiscount(priceNode.path("discount_percent").asInt(0));
        long discountExpiration = priceNode.path("discount_expiration").asLong(0L);
        if (discountExpiration <= 0) {
            discountExpiration = priceNode.path("discount_expiration_date").asLong(0L);
        }
        catalog.setPriceDiscountEndAt(discountExpiration > 0 ? discountExpiration : null);
        String formatted = priceNode.path("final_formatted").asText(null);
        if (!StringUtils.hasText(formatted)) {
            formatted = priceNode.path("initial_formatted").asText(null);
        }
        catalog.setPriceFormatted(StringUtils.hasText(formatted) ? formatted : "暂无价格");
    }

    private void applyDiscountEndAtFromStoreBrowse(GameCatalog catalog) {
        if (catalog.getPriceDiscount() == null || catalog.getPriceDiscount() <= 0) {
            return;
        }
        if (catalog.getPriceDiscountEndAt() != null) {
            return;
        }
        Long endAt = fetchDiscountEndAt(catalog.getAppId());
        if (endAt == null) {
            endAt = fetchDiscountEndFromFeatured(catalog.getAppId());
        }
        if (endAt != null) {
            catalog.setPriceDiscountEndAt(endAt);
        }
    }

    private Long fetchDiscountEndFromFeatured(long appId) {
        String url = UriComponentsBuilder.fromHttpUrl(SteamApiConstants.FEATURED_CATEGORIES_URL)
                .queryParam("cc", steamProperties.getApiCc())
                .queryParam("l", steamProperties.getApiLang())
                .toUriString();
        try {
            String body = restTemplate.getForObject(url, String.class);
            if (!StringUtils.hasText(body)) {
                return null;
            }
            JsonNode root = objectMapper.readTree(body);
            for (String section :
                    List.of("specials", "coming_soon", "top_sellers", "topsellers", "new_releases", "newreleases")) {
                JsonNode items = root.path(section).path("items");
                if (!items.isArray()) {
                    continue;
                }
                for (JsonNode item : items) {
                    if (item.path("id").asLong(0) != appId) {
                        continue;
                    }
                    long expiration = item.path("discount_expiration").asLong(0L);
                    if (expiration <= 0) {
                        expiration = item.path("price").path("discount_expiration").asLong(0L);
                    }
                    if (expiration > 0) {
                        return expiration;
                    }
                }
            }
        } catch (Exception e) {
            log.debug("featuredcategories 失败: appId={}", appId, e);
        }
        return null;
    }

    private Long fetchDiscountEndAt(long appId) {
        try {
            ObjectNode payload = objectMapper.createObjectNode();
            ArrayNode ids = payload.putArray("ids");
            ids.addObject().put("appid", appId);
            ObjectNode context = payload.putObject("context");
            context.put("language", steamProperties.getApiLang());
            context.put(
                    "country_code",
                    steamProperties.getApiCc().toUpperCase(Locale.ROOT));
            context.put("steam_realm", 1);
            ObjectNode dataRequest = payload.putObject("data_request");
            dataRequest.put("include_all_purchase_options", true);

            String inputJson = objectMapper.writeValueAsString(payload);
            StringBuilder urlBuilder = new StringBuilder(SteamApiConstants.STORE_BROWSE_ITEMS_URL)
                    .append("?input_json=")
                    .append(URLEncoder.encode(inputJson, StandardCharsets.UTF_8));
            if (StringUtils.hasText(steamProperties.getWebApiKey())) {
                urlBuilder
                        .append("&key=")
                        .append(URLEncoder.encode(
                                steamProperties.getWebApiKey(), StandardCharsets.UTF_8));
            }
            String body = restTemplate.getForObject(URI.create(urlBuilder.toString()), String.class);
            if (!StringUtils.hasText(body)) {
                return null;
            }
            JsonNode storeItems = objectMapper.readTree(body).path("response").path("store_items");
            if (!storeItems.isArray() || storeItems.isEmpty()) {
                log.debug(
                        "StoreBrowse 无 store_items: appId={}, body={}",
                        appId,
                        body.length() > 400 ? body.substring(0, 400) + "..." : body);
                return null;
            }
            Long latest = null;
            for (JsonNode item : storeItems) {
                Long endAt = readDiscountEndDate(item);
                if (endAt != null && (latest == null || endAt > latest)) {
                    latest = endAt;
                }
            }
            if (latest == null) {
                log.debug(
                        "StoreBrowse 无 discount_end_date: appId={}, purchase={}",
                        appId,
                        storeItems.get(0).path("best_purchase_option"));
            }
            return latest;
        } catch (Exception e) {
            log.warn("StoreBrowse GetItems 失败: appId={}", appId, e);
            return null;
        }
    }

    private Long readDiscountEndDate(JsonNode item) {
        Long latest = readDiscountEndDateFromOption(item.path("best_purchase_option"));
        JsonNode options = item.path("purchase_options");
        if (!options.isArray()) {
            return latest;
        }
        for (JsonNode option : options) {
            Long endAt = readDiscountEndDateFromOption(option);
            if (endAt != null && (latest == null || endAt > latest)) {
                latest = endAt;
            }
        }
        return latest;
    }

    private Long readDiscountEndDateFromOption(JsonNode option) {
        if (option.isMissingNode() || option.isNull()) {
            return null;
        }
        Long latest = null;
        JsonNode discounts = option.path("active_discounts");
        if (!discounts.isArray()) {
            return null;
        }
        for (JsonNode discount : discounts) {
            long endAt = discount.path("discount_end_date").asLong(0L);
            if (endAt > 0 && (latest == null || endAt > latest)) {
                latest = endAt;
            }
        }
        return latest;
    }

    private void applyMetacriticFields(GameCatalog catalog, JsonNode metacriticNode) {
        if (metacriticNode.isMissingNode() || metacriticNode.isNull()) {
            return;
        }
        int score = metacriticNode.path("score").asInt(0);
        if (score > 0) {
            catalog.setMetacriticScore(score);
        }
        catalog.setMetacriticUrl(metacriticNode.path("url").asText(null));
    }

    private void applyAchievementFields(GameCatalog catalog, JsonNode achievementsNode) {
        if (!achievementsNode.isMissingNode() && !achievementsNode.isNull()) {
            int total = achievementsNode.path("total").asInt(0);
            if (total > 0) {
                catalog.setAchievementTotal(total);
            }
        }
        List<GameAchievementVO> schemaAchievements = fetchAchievementSchema(catalog.getAppId());
        if (!schemaAchievements.isEmpty()) {
            catalog.setAchievementHighlights(schemaAchievements);
            if (catalog.getAchievementTotal() == null
                    || catalog.getAchievementTotal() < schemaAchievements.size()) {
                catalog.setAchievementTotal(schemaAchievements.size());
            }
            return;
        }
        if (achievementsNode.isMissingNode() || achievementsNode.isNull()) {
            return;
        }
        List<GameAchievementVO> highlights = new ArrayList<>();
        JsonNode highlighted = achievementsNode.path("highlighted");
        if (highlighted.isArray()) {
            for (JsonNode item : highlighted) {
                String name = item.path("localized_name").asText(null);
                if (!StringUtils.hasText(name)) {
                    name = item.path("name").asText(null);
                }
                String iconUrl = buildAchievementIconUrl(catalog.getAppId(), item.path("path").asText(null));
                if (StringUtils.hasText(name)) {
                    GameAchievementVO vo = new GameAchievementVO();
                    vo.setName(name);
                    vo.setIconUrl(iconUrl);
                    highlights.add(vo);
                }
            }
        }
        catalog.setAchievementHighlights(highlights.isEmpty() ? null : highlights);
    }

    private List<GameAchievementVO> fetchAchievementSchema(long appId) {
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
            List<GameAchievementVO> result = new ArrayList<>();
            for (JsonNode item : achievements) {
                String name = item.path("displayName").asText(null);
                if (!StringUtils.hasText(name)) {
                    name = item.path("name").asText(null);
                }
                if (!StringUtils.hasText(name)) {
                    continue;
                }
                GameAchievementVO vo = new GameAchievementVO();
                vo.setApiName(item.path("name").asText(null));
                vo.setName(name);
                vo.setDescription(item.path("description").asText(null));
                vo.setIconUrl(buildAchievementIconUrl(appId, item.path("icon").asText(null)));
                result.add(vo);
            }
            return result;
        } catch (Exception e) {
            log.warn("GetSchemaForGame 失败: appId={}", appId, e);
            return List.of();
        }
    }

    private void applyPcRequirements(GameCatalog catalog, JsonNode requirementsNode) {
        if (requirementsNode.isMissingNode() || requirementsNode.isNull()) {
            return;
        }
        catalog.setPcRequirementsMin(requirementsNode.path("minimum").asText(null));
        catalog.setPcRequirementsRec(requirementsNode.path("recommended").asText(null));
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
        return StringUtils.hasText(fileName)
                ? SteamApiConstants.STEAM_ICON_URL_PREFIX + appId + "/" + fileName + ".jpg"
                : null;
    }

    private List<GameScreenshotVO> readScreenshots(JsonNode screenshotsNode) {
        List<GameScreenshotVO> result = new ArrayList<>();
        if (!screenshotsNode.isArray()) {
            return result;
        }
        for (JsonNode shot : screenshotsNode) {
            String fullUrl = shot.path("path_full").asText(null);
            if (!StringUtils.hasText(fullUrl)) {
                continue;
            }
            GameScreenshotVO vo = new GameScreenshotVO();
            vo.setFullUrl(fullUrl);
            vo.setThumbnailUrl(shot.path("path_thumbnail").asText(fullUrl));
            result.add(vo);
        }
        return result.isEmpty() ? null : result;
    }

    private List<GameMovieVO> readMovies(JsonNode moviesNode) {
        List<GameMovieVO> result = new ArrayList<>();
        if (!moviesNode.isArray()) {
            return result;
        }
        for (JsonNode movie : moviesNode) {
            String mp4Url = pickVideoUrl(movie.path("mp4"));
            String webmUrl = pickVideoUrl(movie.path("webm"));
            String thumbnail = movie.path("thumbnail").asText(null);
            if (!StringUtils.hasText(mp4Url) && !StringUtils.hasText(webmUrl)) {
                continue;
            }
            GameMovieVO vo = new GameMovieVO();
            vo.setName(movie.path("name").asText("预告片"));
            vo.setThumbnailUrl(thumbnail);
            vo.setMp4Url(mp4Url);
            vo.setWebmUrl(webmUrl);
            result.add(vo);
        }
        return result.isEmpty() ? null : result;
    }

    private String pickVideoUrl(JsonNode formatsNode) {
        if (formatsNode.isMissingNode() || formatsNode.isNull()) {
            return null;
        }
        String max = formatsNode.path("max").asText(null);
        if (StringUtils.hasText(max)) {
            return max;
        }
        return formatsNode.path("480").asText(null);
    }

    private List<String> readCategoryList(JsonNode categoriesNode) {
        List<String> result = new ArrayList<>();
        if (!categoriesNode.isArray()) {
            return result;
        }
        for (JsonNode category : categoriesNode) {
            String description = category.path("description").asText(null);
            if (StringUtils.hasText(description)) {
                result.add(description);
            }
        }
        return result.isEmpty() ? null : result;
    }

    private List<String> readStringList(JsonNode arrayNode) {
        List<String> result = new ArrayList<>();
        if (!arrayNode.isArray()) {
            return result;
        }
        for (JsonNode node : arrayNode) {
            if (node.isTextual()) {
                result.add(node.asText());
            }
        }
        return result;
    }

    private List<String> readGenreList(JsonNode genresNode) {
        List<String> result = new ArrayList<>();
        if (!genresNode.isArray()) {
            return result;
        }
        for (JsonNode genre : genresNode) {
            String description = genre.path("description").asText(null);
            if (StringUtils.hasText(description)) {
                result.add(description);
            }
        }
        return result;
    }

    /**
     * 按榜单偏移量拉取 Steam 轻量候选数据。首屏优先使用精选榜单，后续页使用 Steam 搜索结果
     * 的 start/count 做按需扩展；此方法不请求 appdetails、成就或富媒体详情。
     */
    public List<GameListItemVO> fetchChartGames(String board, int start, int limit) {
        int safeStart = Math.max(0, start);
        int capped = Math.max(1, Math.min(limit, SteamApiConstants.CHART_LIMIT));
        if (safeStart > 0) {
            return fetchSearchChartGames(board, safeStart, capped);
        }
        List<GameListItemVO> games = new ArrayList<>();
        if (!GameBoardConstants.FREE.equalsIgnoreCase(board)) {
            String section = resolveFeaturedSection(board);
            if (StringUtils.hasText(section)) {
                games.addAll(extractFeaturedGames(loadFeaturedCategoriesRoot(), section, capped));
            }
        }
        if (games.size() < capped) {
            games = mergeChartGames(games, fetchSearchChartGames(board, 0, capped));
        }
        if (games.size() > capped) {
            return new ArrayList<>(games.subList(0, capped));
        }
        return games;
    }

    /** 兼容只需要 AppID 的调用方。 */
    public List<Long> fetchChartAppIds(String board, int limit) {
        return fetchChartGames(board, 0, limit).stream()
                .map(GameListItemVO::getAppId)
                .toList();
    }

    /** 兼容只需要 AppID 的分页调用方。 */
    public List<Long> fetchChartAppIds(String board, int start, int limit) {
        return fetchChartGames(board, start, limit).stream()
                .map(GameListItemVO::getAppId)
                .toList();
    }

    private List<GameListItemVO> mergeChartGames(
            List<GameListItemVO> primary,
            List<GameListItemVO> extra) {
        LinkedHashSet<Long> seen = new LinkedHashSet<>();
        List<GameListItemVO> merged = new ArrayList<>();
        for (GameListItemVO game : List.of(primary, extra).stream().flatMap(List::stream).toList()) {
            if (game != null && game.getAppId() != null && seen.add(game.getAppId())) {
                merged.add(game);
            }
        }
        return merged;
    }

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

    private List<String> featuredSectionAliases(String section) {
        if (GameBoardConstants.FEATURED_SECTION_TOP_SELLERS.equals(section)) {
            return List.of("top_sellers", "topsellers");
        }
        if (GameBoardConstants.FEATURED_SECTION_NEW_RELEASES.equals(section)) {
            return List.of("new_releases", "newreleases");
        }
        if (GameBoardConstants.FEATURED_SECTION_SPECIALS.equals(section)) {
            return List.of("specials");
        }
        return List.of(section);
    }

    private JsonNode loadFeaturedCategoriesRoot() {
        String url = UriComponentsBuilder.fromHttpUrl(SteamApiConstants.FEATURED_CATEGORIES_URL)
                .queryParam("cc", steamProperties.getApiCc())
                .queryParam("l", steamProperties.getApiLang())
                .toUriString();
        try {
            String body = restTemplate.getForObject(url, String.class);
            if (!StringUtils.hasText(body)) {
                return objectMapper.createObjectNode();
            }
            return objectMapper.readTree(body);
        } catch (Exception e) {
            log.warn("featuredcategories 拉取失败", e);
            return objectMapper.createObjectNode();
        }
    }

    private List<GameListItemVO> extractFeaturedGames(JsonNode root, String section, int limit) {
        List<GameListItemVO> games = new ArrayList<>();
        for (String alias : featuredSectionAliases(section)) {
            JsonNode items = root.path(alias).path("items");
            if (!items.isArray()) {
                continue;
            }
            for (JsonNode item : items) {
                long appId = item.path("id").asLong(0L);
                if (appId <= 0) {
                    continue;
                }
                // 精选分类可能混入 bundle（type=2），榜单只保留真正的游戏 app。
                if (item.has("type") && item.path("type").asInt(0) != 0) {
                    continue;
                }
                if (games.stream().anyMatch(game -> appId == game.getAppId())) {
                    continue;
                }
                games.add(toChartGame(item, appId,
                        firstText(item, "header_image", "large_capsule_image", "small_capsule_image")));
                if (games.size() >= limit) {
                    return games;
                }
            }
        }
        return games;
    }

    private List<GameListItemVO> fetchSearchChartGames(String board, int start, int limit) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(SteamApiConstants.STORE_SEARCH_URL)
                .queryParam("json", 1)
                .queryParam("start", Math.max(0, start))
                .queryParam("count", limit)
                .queryParam("category1", 998)
                .queryParam("cc", steamProperties.getApiCc())
                .queryParam("l", steamProperties.getApiLang());
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
            String body = restTemplate.getForObject(builder.toUriString(), String.class);
            if (!StringUtils.hasText(body)) {
                throw new IllegalStateException("Steam 榜单接口返回空响应");
            }
            JsonNode items = objectMapper.readTree(body).path("items");
            if (!items.isArray()) {
                throw new IllegalStateException("Steam 榜单接口缺少 items");
            }
            List<GameListItemVO> games = new ArrayList<>();
            for (JsonNode item : items) {
                Long appId = resolveSearchAppId(item);
                if (appId == null || appId <= 0) {
                    continue;
                }
                if (games.stream().anyMatch(game -> appId.equals(game.getAppId()))) {
                    continue;
                }
                games.add(toChartGame(item, appId,
                        firstText(item, "header_image", "logo")));
                if (games.size() >= limit) {
                    break;
                }
            }
            return games;
        } catch (Exception e) {
            log.warn("Steam 榜单搜索失败 board={}", board, e);
            throw new IllegalStateException("Steam 榜单搜索失败: " + board, e);
        }
    }

    private GameListItemVO toChartGame(JsonNode item, long appId, String coverUrl) {
        GameListItemVO game = new GameListItemVO();
        game.setAppId(appId);
        game.setName(item.path("name").asText("游戏 " + appId));
        game.setCoverUrl(coverUrl);
        JsonNode price = item.path("price");
        if (price.isMissingNode() || price.isNull()) {
            price = item;
        }
        if (hasPrice(price)) {
            GamePriceVO priceVO = new GamePriceVO();
            priceVO.setFree(price.path("final_price").asInt(0) == 0);
            priceVO.setCurrency(price.path("currency").asText(null));
            priceVO.setInitial(firstInt(price, "initial", "initial_price", "original_price"));
            priceVO.setFinalPrice(firstInt(price, "final", "final_price"));
            priceVO.setDiscountPercent(price.path("discount_percent").asInt(0));
            priceVO.setDiscountEndAt(firstLong(price, "discount_expiration", "discount_expiration_date"));
            priceVO.setFormatted(firstText(price, "final_formatted", "initial_formatted"));
            game.setPrice(priceVO);
        }
        return game;
    }

    private boolean hasPrice(JsonNode price) {
        return price.has("final_price") || price.has("final")
                || price.has("original_price") || price.has("initial");
    }

    private String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = node.path(field).asText(null);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private Integer firstInt(JsonNode node, String... fields) {
        for (String field : fields) {
            if (node.has(field) && !node.path(field).isNull()) {
                return node.path(field).asInt();
            }
        }
        return null;
    }

    private Long firstLong(JsonNode node, String... fields) {
        for (String field : fields) {
            if (node.has(field) && !node.path(field).isNull()) {
                long value = node.path(field).asLong(0L);
                if (value > 0) {
                    return value;
                }
            }
        }
        return null;
    }

    private Long resolveSearchAppId(JsonNode item) {
        long appId = item.path("id").asLong(0L);
        if (appId > 0) {
            return appId;
        }
        String logo = item.path("logo").asText("");
        if (!StringUtils.hasText(logo)) {
            logo = item.path("header_image").asText("");
        }
        if (!StringUtils.hasText(logo)) {
            return null;
        }
        int marker = logo.indexOf("/apps/");
        if (marker < 0) {
            return null;
        }
        int start = marker + "/apps/".length();
        int end = logo.indexOf('/', start);
        String idText = end > start ? logo.substring(start, end) : logo.substring(start);
        try {
            return Long.parseLong(idText);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private List<Long> fetchFreeChartAppIds(int limit) {
        return fetchSearchChartGames(GameBoardConstants.FREE, 0, limit).stream()
                .map(GameListItemVO::getAppId)
                .toList();
    }
}
