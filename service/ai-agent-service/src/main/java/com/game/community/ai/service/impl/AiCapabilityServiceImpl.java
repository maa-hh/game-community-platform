package com.game.community.ai.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.community.ai.agent.AgentModelRegistry;
import com.game.community.ai.service.AiCapabilityService;
import com.game.community.model.dto.aiagent.AiEmbeddingRequest;
import com.game.community.model.dto.aiagent.AiSearchTermsRequest;
import com.game.community.model.vo.aiagent.AiEmbeddingResponseVO;
import com.game.community.model.vo.aiagent.AiSearchTermsResponseVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ResourceLoader;
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
@Slf4j
public class AiCapabilityServiceImpl implements AiCapabilityService {

    private static final Pattern JSON_ARRAY_PATTERN = Pattern.compile("\\[[\\s\\S]*?]");
    private static final int MAX_SEARCH_TERMS = 5;

    private final AgentModelRegistry modelRegistry;
    private final ObjectMapper objectMapper;
    private final PromptTemplate searchTermsSystemPrompt;

    @Autowired
    public AiCapabilityServiceImpl(AgentModelRegistry modelRegistry,
                                   ObjectMapper objectMapper,
                                   ResourceLoader resourceLoader,
                                   @Value("${ai-agent.search.search-terms-system-prompt-path:classpath:prompts/search-terms-system.st}")
                                   String searchTermsSystemPromptPath) {
        this.modelRegistry = modelRegistry;
        this.objectMapper = objectMapper;
        this.searchTermsSystemPrompt = new PromptTemplate(
                resourceLoader.getResource(searchTermsSystemPromptPath));
    }

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
        int maxTerms = Math.min(Math.max(request.getMaxTerms() == null ? MAX_SEARCH_TERMS : request.getMaxTerms(), 1),
                MAX_SEARCH_TERMS);
        AgentModelRegistry.ModelClient client = modelRegistry.chat(request.getProvider());
        String responseText = client.client().prompt()
                .system(searchTermsSystemPrompt.render())
                .user(buildSearchTermPrompt(request))
                .call()
                .content();
        List<String> terms = parseTerms(responseText, maxTerms);
        log.info("AI 搜索扩词完成: provider={}, model={}, requestedMaxTerms={}, actualTerms={}",
                client.provider(), client.model(), maxTerms, terms.size());
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
