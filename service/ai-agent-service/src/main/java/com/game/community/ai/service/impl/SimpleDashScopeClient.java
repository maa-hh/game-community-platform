package com.game.community.ai.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.community.ai.config.AiAgentProperties;
import com.game.community.model.vo.aiagent.AiChatMessageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class SimpleDashScopeClient {

    private final AiAgentProperties properties;
    private final ObjectMapper objectMapper;

    @Value("${spring.ai.dashscope.api-key}")
    private String apiKey;

    private final RestClient restClient = RestClient.builder().build();

    public List<Float> embedding(String text) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("model", properties.getEmbeddingModel());
        payload.put("input", text);
        JsonNode response = restClient.post()
                .uri(properties.getEmbeddingEndpoint())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(JsonNode.class);
        JsonNode embeddingNode = response.path("data").get(0).path("embedding");
        List<Float> vector = new ArrayList<>(embeddingNode.size());
        for (JsonNode node : embeddingNode) {
            vector.add(node.floatValue());
        }
        return vector;
    }

    public String chat(String systemPrompt, List<AiChatMessageVO> history, String userMessage) {
        List<Map<String, String>> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(message("system", systemPrompt));
        }
        if (history != null) {
            for (AiChatMessageVO item : history) {
                messages.add(message(item.getRole(), item.getContent()));
            }
        }
        messages.add(message("user", userMessage));
        Map<String, Object> payload = new HashMap<>();
        payload.put("model", properties.getChatModel());
        payload.put("messages", messages);
        JsonNode response = restClient.post()
                .uri(properties.getChatEndpoint())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(JsonNode.class);
        return response.path("choices").get(0).path("message").path("content").asText("");
    }

    private Map<String, String> message(String role, String content) {
        Map<String, String> map = new HashMap<>();
        map.put("role", role);
        map.put("content", content);
        return map;
    }
}
