package com.game.community.steam.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.community.common.constant.steam.SteamApiConstants;
import com.game.community.model.payload.steam.SteamReviewSummaryPayload;
import com.game.community.steam.config.SteamProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Steam 评价接口客户端。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SteamReviewClient {

    private final RestTemplate restTemplate;
    private final SteamProperties steamProperties;
    private final ObjectMapper objectMapper;

    /** 拉取 Steam 评价总数和好评率，失败时返回空结果。 */
    public SteamReviewSummaryPayload fetchReviewSummary(long appId) {
        String url = UriComponentsBuilder
                .fromHttpUrl(SteamApiConstants.APP_REVIEWS_URL + "/" + appId)
                .queryParam("json", 1)
                // 评分要与 Steam 商店总评价一致，不能沿用 appdetails 的中文语言过滤。
                .queryParam("language", SteamApiConstants.REVIEW_ALL_LANGUAGE)
                // num_per_page=0 在部分游戏上会返回空汇总；请求一条即可拿到总数。
                .queryParam("num_per_page", SteamApiConstants.REVIEW_REQUEST_PAGE_SIZE)
                .queryParam("filter", SteamApiConstants.REVIEW_FILTER_ALL)
                .queryParam("purchase_type", SteamApiConstants.REVIEW_ALL_PURCHASE_TYPE)
                .toUriString();
        try {
            JsonNode root = objectMapper.readTree(restTemplate.getForObject(url, String.class));
            if (!root.path("success").asBoolean(false)) {
                return null;
            }
            JsonNode summary = root.path("query_summary");
            int totalReviews = summary.path("total_reviews").asInt(0);
            if (totalReviews <= 0) {
                return new SteamReviewSummaryPayload(0, 0);
            }
            int totalPositive = summary.path("total_positive").asInt(0);
            int positivePercent = Math.min(100,
                    Math.max(0, Math.round(totalPositive * 100f / totalReviews)));
            return new SteamReviewSummaryPayload(positivePercent, totalReviews);
        } catch (Exception e) {
            log.warn("Steam 评价摘要获取失败: appId={}", appId, e);
            return null;
        }
    }
}
