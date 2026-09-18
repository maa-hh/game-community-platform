package com.game.community.search.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.game.community.common.constant.search.SearchConstants;
import com.game.community.model.dto.aiagent.AiSearchTermsRequest;
import com.game.community.model.elasticsearch.ArticleDocument;
import com.game.community.model.message.AiTaskRequestMessage;
import com.game.community.model.message.SearchAiContext;
import com.game.community.search.config.SearchAiProperties;
import com.game.community.search.event.AiTaskProducer;
import com.game.community.search.service.AiSuggestTermService;
import com.game.community.search.service.SuggestTermService;
import com.game.community.search.service.SuggestTermService.TermSeed;
import com.game.community.search.util.SuggestTermNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiSuggestTermServiceImpl implements AiSuggestTermService {

    private final AiTaskProducer aiTaskProducer;
    private final SearchAiProperties searchAiProperties;
    private final SuggestTermService suggestTermService;
    private final ElasticsearchClient elasticsearchClient;
    private final DataSource dataSource;

    @Override
    public void expandAsync(Long articleId, ArticleDocument document) {
        if (articleId == null || document == null) {
            return;
        }
        if (!searchAiProperties.isEnabled() || !searchAiProperties.isAiSuggestEnabled()) {
            return;
        }
        try {
            AiSearchTermsRequest request = new AiSearchTermsRequest();
            request.setTitle(document.getTitle());
            request.setSummary(document.getSummary());
            request.setContent(document.getContent());
            request.setProvider(searchAiProperties.getSearchTermsProvider());
            SearchAiContext context = new SearchAiContext();
            context.setOperation(SearchAiContext.SEARCH_TERMS);
            context.setDocument(document);
            aiTaskProducer.send(AiTaskRequestMessage.SEARCH_TERMS, articleId, request, context);
        } catch (Exception e) {
            log.warn("AI 扩词失败: articleId={}, reason={}", articleId, e.getMessage());
        }
    }

    /**
     * AI 请求完成后再次校验文章版本，并用 MySQL named lock 串行化同一文章的异步任务。
     * 这样旧任务即使晚于新任务返回，也会在写入前发现 ES 中已经是新版本并直接丢弃。
     */
    public void writeIfCurrent(Long articleId, ArticleDocument expectedDocument, List<String> terms) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            if (!tryAcquireLock(connection, articleId)) {
                return;
            }
            try {
                if (!isCurrentDocument(articleId, expectedDocument)) {
                    log.info("跳过过期 AI 扩词结果: articleId={}", articleId);
                    return;
                }
                suggestTermService.expireArticleSourceTypes(articleId,
                        List.of(SearchConstants.SUGGEST_SOURCE_AI));
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
            } finally {
                releaseLock(connection, articleId);
            }
        }
    }

    private boolean isCurrentDocument(Long articleId, ArticleDocument expectedDocument) throws Exception {
        var current = elasticsearchClient.get(request -> request
                        .index(SearchConstants.ARTICLE_INDEX)
                        .id(String.valueOf(articleId)), ArticleDocument.class);
        if (!current.found() || current.source() == null) {
            return false;
        }
        ArticleDocument actual = current.source();
        if (expectedDocument.getUpdateTime() != null) {
            return Objects.equals(expectedDocument.getUpdateTime(), actual.getUpdateTime());
        }
        return Objects.equals(expectedDocument.getTitle(), actual.getTitle())
                && Objects.equals(expectedDocument.getContent(), actual.getContent());
    }

    private boolean tryAcquireLock(Connection connection, Long articleId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("SELECT GET_LOCK(?, 0)")) {
            statement.setString(1, SearchConstants.AI_SUGGEST_LOCK_PREFIX + articleId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getInt(1) == 1;
            }
        }
    }

    private void releaseLock(Connection connection, Long articleId) {
        try (PreparedStatement statement = connection.prepareStatement("SELECT RELEASE_LOCK(?)")) {
            statement.setString(1, SearchConstants.AI_SUGGEST_LOCK_PREFIX + articleId);
            statement.execute();
        } catch (Exception e) {
            log.warn("释放 AI 扩词锁失败: articleId={}, reason={}", articleId, e.getMessage());
        }
    }

}
