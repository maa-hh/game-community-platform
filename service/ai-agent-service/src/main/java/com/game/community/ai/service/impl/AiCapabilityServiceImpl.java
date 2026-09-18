package com.game.community.ai.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.community.ai.agent.AgentModelRegistry;
import com.game.community.ai.service.AiCapabilityService;
import com.game.community.model.dto.aiagent.AiEmbeddingRequest;
import com.game.community.model.dto.aiagent.AiSearchTermsRequest;
import com.game.community.model.vo.aiagent.AiEmbeddingResponseVO;
import com.game.community.model.vo.aiagent.AiSearchTermsResponseVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 为其他微服务提供无状态、可复用的 AI 基础能力。 */
@Service
@RequiredArgsConstructor
public class AiCapabilityServiceImpl implements AiCapabilityService {

    private static final String SEARCH_TERM_SYSTEM_PROMPT = """
            你是游戏社区搜索运营助手。根据帖子标题、摘要和正文，生成适合用户搜索的简洁关键词。
            只输出 JSON 字符串数组，不要 markdown，不要解释；每词 2-8 个汉字或常见英文缩写，最多 5 个。
            优先保留游戏名、玩法、角色、攻略对象等可检索概念。
            """;

    private static final Pattern JSON_ARRAY_PATTERN = Pattern.compile("\\[[\\s\\S]*?]");

    private final AgentModelRegistry modelRegistry;
    private final ObjectMapper objectMapper;

    @Override
    public AiEmbeddingResponseVO embedding(AiEmbeddingRequest request) {
        AgentModelRegistry.EmbeddingModelClient client = modelRegistry.embedding(request.getProvider());
        List<Float> vector = client.client().embed(request.getText());
        if (vector == null || vector.isEmpty()) {
            throw new IllegalStateException("embedding 服务返回为空");
        }
        AiEmbeddingResponseVO response = new AiEmbeddingResponseVO();
        response.setVector(vector);
        response.setProvider(client.provider());
        response.setModel(client.model());
        return response;
    }

    @Override
    public AiSearchTermsResponseVO expandSearchTerms(AiSearchTermsRequest request) {
        int maxTerms = Math.min(Math.max(request.getMaxTerms() == null ? 5 : request.getMaxTerms(), 1), 10);
        AgentModelRegistry.ModelClient client = modelRegistry.chat(request.getProvider());
        String responseText = client.client().prompt()
                .system(SEARCH_TERM_SYSTEM_PROMPT)
                .user(buildSearchTermPrompt(request))
                .call()
                .content();
        List<String> terms = parseTerms(responseText, maxTerms);
        AiSearchTermsResponseVO response = new AiSearchTermsResponseVO();
        response.setTerms(terms);
        response.setProvider(client.provider());
        response.setModel(client.model());
        return response;
    }

    private String buildSearchTermPrompt(AiSearchTermsRequest request) {
        return "标题：" + normalize(request.getTitle()) + '\n'
                + "摘要：" + normalize(request.getSummary()) + '\n'
                + "正文：" + limit(request.getContent(), 2000);
    }

    private List<String> parseTerms(String response, int maxTerms) {
        if (!StringUtils.hasText(response)) {
            return List.of();
        }
        String candidate = response.trim();
        List<String> parsed = readArray(candidate);
        if (parsed.isEmpty()) {
            Matcher matcher = JSON_ARRAY_PATTERN.matcher(candidate);
            if (matcher.find()) {
                parsed = readArray(matcher.group());
            }
        }
        Set<String> terms = new LinkedHashSet<>();
        for (String term : parsed) {
            if (StringUtils.hasText(term) && terms.size() < maxTerms) {
                terms.add(term.trim());
            }
        }
        return new ArrayList<>(terms);
    }

    private List<String> readArray(String value) {
        try {
            return objectMapper.readValue(value, new TypeReference<>() {
            });
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private String limit(String value, int maxLength) {
        String text = normalize(value);
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
