package com.game.community.search.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.game.community.model.dto.search.SearchCorrectVO;
import com.game.community.model.dto.search.SearchResult;
import com.game.community.model.dto.search.SuggestionPageDTO;
import com.game.community.model.elasticsearch.SuggestDocument;
import com.game.community.search.initIndex.InitElasticsearchIndex;
import com.game.community.search.service.ElasticsearchService;
import com.game.community.search.service.SuggestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SuggestServiceImpl implements SuggestService {

    private final ElasticsearchService elasticsearchService;
    private final ElasticsearchClient elasticsearchClient;

    @Override
    public List<SuggestDocument> suggest(String prefix) {
        if (!StringUtils.hasText(prefix)) {
            return List.of();
        }
        try {
            SearchRequest request = SearchRequest.of(s -> s
                    .index(InitElasticsearchIndex.SUGGEST_INDEX)
                    .query(q -> q.bool(b -> b
                            .should(Query.of(sq -> sq.matchPhrasePrefix(m -> m.field("suggest").query(prefix.trim()))))
                            .should(Query.of(sq -> sq.match(m -> m.field("suggestNgram").query(prefix.trim()).fuzziness("AUTO"))))
                            .minimumShouldMatch("1")
                    ))
                    .size(10)
            );
            SearchResponse<SuggestDocument> response = elasticsearchClient.search(request, SuggestDocument.class);
            Map<String, SuggestDocument> dedup = new LinkedHashMap<>();
            for (Hit<SuggestDocument> hit : response.hits().hits()) {
                SuggestDocument doc = hit.source();
                if (doc != null && StringUtils.hasText(doc.getSuggest())) {
                    dedup.putIfAbsent(doc.getSuggest(), doc);
                }
            }
            return new ArrayList<>(dedup.values());
        } catch (Exception e) {
            log.warn("获取搜索建议失败: prefix={}", prefix, e);
            return List.of();
        }
    }

    @Override
    public SearchCorrectVO correct(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return SearchCorrectVO.noNeed();
        }
        List<SuggestDocument> candidates = suggest(keyword.trim());
        return candidates.stream()
                .filter(item -> StringUtils.hasText(item.getSuggest()))
                .map(item -> SearchCorrectVO.of(keyword, item.getSuggest(), "FUZZY", levenshteinDistance(keyword, item.getSuggest())))
                .filter(item -> item.getDistance() != null && item.getDistance() <= 2)
                .min(Comparator.comparing(SearchCorrectVO::getDistance))
                .orElse(SearchCorrectVO.noNeed());
    }

    @Override
    public SearchResult getSuggestions(SuggestionPageDTO pageDTO) {
        int page = Math.max(pageDTO.getPage() == null ? 1 : pageDTO.getPage(), 1);
        int size = Math.min(Math.max(pageDTO.getSize() == null ? 10 : pageDTO.getSize(), 1), 50);
        try {
            BoolQuery.Builder boolQuery = new BoolQuery.Builder();
            if (StringUtils.hasText(pageDTO.getKeyword())) {
                boolQuery.must(Query.of(q -> q.match(m -> m.field("suggest").query(pageDTO.getKeyword().trim()))));
            } else {
                boolQuery.must(Query.of(q -> q.matchAll(m -> m)));
            }
            SearchResponse<SuggestDocument> response = elasticsearchClient.search(SearchRequest.of(s -> s
                    .index(InitElasticsearchIndex.SUGGEST_INDEX)
                    .query(Query.of(q -> q.bool(boolQuery.build())))
                    .from((page - 1) * size)
                    .size(size)
            ), SuggestDocument.class);
            SearchResult result = new SearchResult();
            result.setPage((long) page);
            result.setSize((long) size);
            result.setTotal(response.hits().total() == null ? 0L : response.hits().total().value());
            result.setList(response.hits().hits().stream().map(Hit::source).filter(item -> item != null).toList());
            return result;
        } catch (IOException e) {
            SearchResult result = new SearchResult();
            result.setErrorMsg("获取建议词列表失败，请稍后重试");
            return result;
        }
    }

    @Override
    public void batchAddSuggestions(List<SuggestDocument> documents) {
        if (documents == null || documents.isEmpty()) {
            return;
        }
        List<SuggestDocument> validDocuments = documents.stream()
                .filter(item -> item != null && StringUtils.hasText(item.getSuggest()))
                .peek(item -> item.setSuggestNgram(item.getSuggest().trim()))
                .toList();
        elasticsearchService.batchAddSuggestions(validDocuments);
    }

    @Override
    public void deleteSuggestion(Long id) {
        elasticsearchService.deleteSuggestion(id);
    }

    @Override
    public void loadSuggestionsFromXls(MultipartFile file) {
        List<SuggestDocument> documents = new ArrayList<>();
        try (InputStream inputStream = file.getInputStream(); Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || row.getCell(0) == null) {
                    continue;
                }
                String keyword = getCellValue(row.getCell(0));
                if (StringUtils.hasText(keyword)) {
                    SuggestDocument doc = new SuggestDocument();
                    doc.setSuggest(keyword.trim());
                    documents.add(doc);
                }
            }
            batchAddSuggestions(documents);
        } catch (Exception e) {
            throw new IllegalStateException("从xls文件加载建议词失败", e);
        }
    }

    private String getCellValue(Cell cell) {
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> "";
        };
    }

    private int levenshteinDistance(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];
        for (int i = 0; i <= s1.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= s2.length(); j++) {
            dp[0][j] = j;
        }
        for (int i = 1; i <= s1.length(); i++) {
            for (int j = 1; j <= s2.length(); j++) {
                dp[i][j] = s1.charAt(i - 1) == s2.charAt(j - 1)
                        ? dp[i - 1][j - 1]
                        : 1 + Math.min(Math.min(dp[i - 1][j], dp[i][j - 1]), dp[i - 1][j - 1]);
            }
        }
        return dp[s1.length()][s2.length()];
    }
}
