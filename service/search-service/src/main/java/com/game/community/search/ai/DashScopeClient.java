package com.game.community.search.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.game.community.search.config.SearchAiProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import com.game.community.common.constant.search.SearchConstants;

import java.time.Duration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class DashScopeClient {

    private final SearchAiProperties properties;

    private final RestClient restClient;

    public DashScopeClient(SearchAiProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(Math.max(properties.getConnectTimeoutMs(), 100)));
        requestFactory.setReadTimeout(Duration.ofMillis(Math.max(properties.getReadTimeoutMs(), 100)));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    public boolean isConfigured() {
        return StringUtils.hasText(properties.getApiKey()) && properties.isEnabled();
    }

    public List<Float> embedding(String text) {
        if (!isConfigured() || !StringUtils.hasText(text)) {
            return List.of();
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("model", properties.getEmbeddingModel());
        payload.put("input", text);
        JsonNode response;
        try {
            response = restClient.post()
                    .uri(properties.getEmbeddingEndpoint())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            log.warn("向量服务请求失败: {}", e.getMessage());
            return List.of();
        }
        if (response == null || !response.path("data").isArray() || response.path("data").isEmpty()) {
            return List.of();
        }
        JsonNode embeddingNode = response.path("data").get(0).path("embedding");
        if (!embeddingNode.isArray() || embeddingNode.isEmpty()) {
            return List.of();
        }
        List<Float> vector = new ArrayList<>(embeddingNode.size());
        for (JsonNode node : embeddingNode) {
            vector.add(node.floatValue());
        }
        if (vector.size() != SearchConstants.EMBEDDING_DIMS) {
            log.warn("向量维度不匹配: expected={}, actual={}", SearchConstants.EMBEDDING_DIMS, vector.size());
            return List.of();
        }
        return vector;
    }

    public String chat(String systemPrompt, String userMessage) {
        if (!isConfigured() || !StringUtils.hasText(userMessage)) {
            return "";
        }
        List<Map<String, String>> messages = new ArrayList<>();
        if (StringUtils.hasText(systemPrompt)) {
            messages.add(message("system", systemPrompt));
        }
        messages.add(message("user", userMessage));
        Map<String, Object> payload = new HashMap<>();
        payload.put("model", properties.getChatModel());
        payload.put("messages", messages);
        JsonNode response;
        try {
            response = restClient.post()
                    .uri(properties.getChatEndpoint())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            log.warn("对话服务请求失败: {}", e.getMessage());
            return "";
        }
        if (response == null) {
            return "";
        }
        return response.path("choices").path(0).path("message").path("content").asText("");
    }

    private Map<String, String> message(String role, String content) {
        Map<String, String> map = new HashMap<>();
        map.put("role", role);
        map.put("content", content);
        return map;
    }
}
