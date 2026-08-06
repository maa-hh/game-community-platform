package com.game.community.search.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.AnalyzeRequest;
import co.elastic.clients.elasticsearch.indices.analyze.AnalyzeToken;
import com.game.community.common.constant.search.SearchConstants;
import com.game.community.model.elasticsearch.SuggestDocument;
import com.game.community.search.initIndex.InitElasticsearchIndex;
import com.game.community.search.service.ElasticsearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class SuggestTermTokenizer {

    private static final Set<String> STOP_WORDS = Set.of(
            "的", "了", "吗", "呢", "啊", "吧", "是", "在", "和", "与", "及", "或", "一个", "我们", "你们", "他们"
    );

    private final ElasticsearchClient elasticsearchClient;

    public List<String> tokenize(String text) {
        if (!StringUtils.hasText(text)) {
            return List.of();
        }
        Set<String> tokens = new LinkedHashSet<>();
        try {
            List<AnalyzeToken> analyzed = elasticsearchClient.indices()
                    .analyze(AnalyzeRequest.of(a -> a
                            .index(SearchConstants.ARTICLE_INDEX)
                            .analyzer("ik_smart")
                            .text(text)))
                    .tokens();
            for (AnalyzeToken token : analyzed) {
                addToken(tokens, token.token());
            }
        } catch (IOException e) {
            log.warn("IK 分词失败，回退简单切分: {}", e.getMessage());
            fallbackSplit(text, tokens);
        }
        if (tokens.isEmpty()) {
            fallbackSplit(text, tokens);
        }
        return new ArrayList<>(tokens);
    }

    private void fallbackSplit(String text, Set<String> tokens) {
        for (String part : text.split("[\\s,，。！？!?、；;：:\\-]+")) {
            addToken(tokens, part);
        }
    }

    private void addToken(Set<String> tokens, String raw) {
        String normalized = raw == null ? null : raw.trim();
        if (!StringUtils.hasText(normalized)) {
            return;
        }
        if (normalized.length() < SearchConstants.TERM_MIN_LEN) {
            return;
        }
        if (STOP_WORDS.contains(normalized)) {
            return;
        }
        tokens.add(normalized);
    }
}
