package com.game.community.ai.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.community.ai.config.AiAgentProperties;
import lombok.RequiredArgsConstructor;
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

    private final RestClient restClient = RestClient.builder().build();

    public List<Float> embedding(String text) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("model", properties.getEmbeddingModel());
        payload.put("input", text);
        JsonNode response = restClient.post()
                .uri(properties.getEmbeddingEndpoint())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
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

}
