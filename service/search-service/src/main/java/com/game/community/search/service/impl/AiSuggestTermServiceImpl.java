package com.game.community.search.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.community.common.constant.search.SearchConstants;
import com.game.community.model.elasticsearch.ArticleDocument;
import com.game.community.search.ai.DashScopeClient;
import com.game.community.search.config.SearchAiProperties;
import com.game.community.search.service.AiSuggestTermService;
import com.game.community.search.service.SuggestTermService;
import com.game.community.search.service.SuggestTermService.TermSeed;
import com.game.community.search.util.SuggestTermNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiSuggestTermServiceImpl implements AiSuggestTermService {

    private static final Pattern JSON_ARRAY_PATTERN = Pattern.compile("\\[[\\s\\S]*?]");

    private static final String SYSTEM_PROMPT = """
            你是游戏社区搜索运营助手。根据帖子标题、摘要和分类，生成适合用户搜索的中文关键词。
            要求：
            1. 只输出 JSON 字符串数组，不要 markdown，不要解释
            2. 每词 2-8 个汉字或常见英文缩写
            3. 最多 5 个，与游戏/内容强相关
            """;

    private final DashScopeClient dashScopeClient;
    private final SearchAiProperties searchAiProperties;
    private final SuggestTermService suggestTermService;
    private final ObjectMapper objectMapper;

    @Override
    @Async("aiExecutor")
    public void expandAsync(Long articleId, ArticleDocument document) {
        if (articleId == null || document == null) {
            return;
        }
        if (!searchAiProperties.isAiSuggestEnabled() || !dashScopeClient.isConfigured()) {
            return;
        }
        try {
            suggestTermService.expireArticleSourceTypes(articleId, List.of(SearchConstants.SUGGEST_SOURCE_AI));
            String response = dashScopeClient.chat(SYSTEM_PROMPT, buildUserPrompt(document));
            List<String> terms = parseTerms(response);
            if (terms.isEmpty()) {
                return;
            }
            int count = 0;
            for (String term : terms) {
                if (count >= SearchConstants.AI_SUGGEST_MAX_TERMS) {
                    break;
                }
                String normalized = SuggestTermNormalizer.normalize(term);
                if (!StringUtils.hasText(normalized)) {
                    continue;
                }
                suggestTermService.upsertActive(new TermSeed(
                        normalized,
                        SearchConstants.SUGGEST_SOURCE_AI,
                        articleId,
                        SearchConstants.WEIGHT_AI,
                        false));
                count++;
            }
            log.info("AI 扩词完成: articleId={}, count={}", articleId, count);
        } catch (Exception e) {
            log.warn("AI 扩词失败: articleId={}, reason={}", articleId, e.getMessage());
        }
    }

    private String buildUserPrompt(ArticleDocument document) {
        StringBuilder builder = new StringBuilder();
        builder.append("标题：").append(nullToEmpty(document.getTitle())).append('\n');
        builder.append("摘要：").append(nullToEmpty(document.getSummary())).append('\n');
        if (!CollectionUtils.isEmpty(document.getCategoryNames())) {
            builder.append("分类：").append(String.join("、", document.getCategoryNames()));
        } else {
            builder.append("分类：").append(nullToEmpty(document.getCategoryName()));
        }
        return builder.toString();
    }

    private List<String> parseTerms(String response) {
        if (!StringUtils.hasText(response)) {
            return List.of();
        }
        String trimmed = response.trim();
        Set<String> terms = new LinkedHashSet<>();
        try {
            List<String> direct = objectMapper.readValue(trimmed, new TypeReference<>() {
            });
            direct.stream().filter(StringUtils::hasText).map(String::trim).forEach(terms::add);
            if (!terms.isEmpty()) {
                return new ArrayList<>(terms);
            }
        } catch (Exception ignored) {
            // fallback to regex extraction
        }
        Matcher matcher = JSON_ARRAY_PATTERN.matcher(trimmed);
        if (matcher.find()) {
            try {
                List<String> extracted = objectMapper.readValue(matcher.group(), new TypeReference<>() {
                });
                extracted.stream().filter(StringUtils::hasText).map(String::trim).forEach(terms::add);
            } catch (Exception e) {
                log.debug("AI 扩词 JSON 解析失败: {}", e.getMessage());
            }
        }
        return new ArrayList<>(terms);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
