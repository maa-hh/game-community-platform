package com.game.community.search.controller;

import com.game.community.common.annotation.AdminCheck;
import com.game.community.common.annotation.LoginCheck;
import com.game.community.model.base.PageResult;
import com.game.community.model.base.Result;
import com.game.community.model.dto.search.SearchCorrectVO;
import com.game.community.model.dto.search.SearchPageDTO;
import com.game.community.model.dto.search.SuggestionPageDTO;
import com.game.community.model.elasticsearch.SuggestDocument;
import com.game.community.model.vo.article.ArticleSearchItemVO;
import com.game.community.search.service.ArticleSearchService;
import com.game.community.search.service.ArticleSyncService;
import com.game.community.search.service.SearchRecordService;
import com.game.community.search.service.SuggestService;
import com.game.community.utils.ThreadLocal.UserThreadLocal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "搜索服务")
@RestController
@RequestMapping("/search")
@RequiredArgsConstructor
public class SearchController {

    private final ArticleSearchService articleSearchService;
    private final SuggestService suggestService;
    private final SearchRecordService searchRecordService;
    private final ArticleSyncService articleSyncService;

    @Operation(summary = "文章搜索")
    @GetMapping("/article")
    @LoginCheck
    public PageResult<ArticleSearchItemVO> searchArticle(@ModelAttribute SearchPageDTO searchDTO) {
        var searchResult = articleSearchService.search(searchDTO);
        if (!searchResult.isSuccess()) {
            PageResult<ArticleSearchItemVO> error = new PageResult<>();
            error.setCode(500);
            error.setMessage(searchResult.getErrorMsg());
            error.setPage(1L);
            error.setSize(0L);
            error.setTotal(0L);
            return error;
        }
        if (searchDTO.getKeyword() != null && !searchDTO.getKeyword().isBlank()) {
            searchRecordService.addRecord(UserThreadLocal.getUserId(), searchDTO.getKeyword());
        }
        return PageResult.of(
                (List<ArticleSearchItemVO>) searchResult.getList(),
                searchResult.getPage(),
                searchResult.getSize(),
                searchResult.getTotal()
        );
    }

    @Operation(summary = "搜索建议")
    @GetMapping("/suggest")
    @LoginCheck
    public Result<List<SuggestDocument>> suggest(@RequestParam("prefix") String prefix) {
        return Result.success(suggestService.suggest(prefix));
    }

    @Operation(summary = "搜索纠错")
    @GetMapping("/correct")
    @LoginCheck
    public Result<SearchCorrectVO> correct(@RequestParam("keyword") String keyword) {
        return Result.success(suggestService.correct(keyword));
    }

    @Operation(summary = "分页获取建议词列表")
    @GetMapping("/suggest/list")
    @AdminCheck
    public PageResult<SuggestDocument> getSuggestions(@ModelAttribute SuggestionPageDTO pageDTO) {
        var result = suggestService.getSuggestions(pageDTO);
        return PageResult.of((List<SuggestDocument>) result.getList(), result.getPage(), result.getSize(), result.getTotal());
    }

    @Operation(summary = "批量添加建议词")
    @PostMapping("/suggest/batch")
    @AdminCheck
    public Result<Void> batchAdd(@RequestBody List<SuggestDocument> documents) {
        suggestService.batchAddSuggestions(documents);
        return Result.success(null);
    }

    @Operation(summary = "删除建议词")
    @DeleteMapping("/suggest/{id}")
    @AdminCheck
    public Result<Void> delete(@PathVariable("id") Long id) {
        suggestService.deleteSuggestion(id);
        return Result.success(null);
    }

    @Operation(summary = "从 xls/xlsx 文件加载建议词")
    @PostMapping("/suggest/loadFromXls")
    @AdminCheck
    public Result<Void> loadFromXls(@RequestParam("file") MultipartFile file) {
        suggestService.loadSuggestionsFromXls(file);
        return Result.success(null);
    }

    @Operation(summary = "重建已发布文章索引")
    @PostMapping("/article/rebuild")
    @AdminCheck
    public Result<Void> rebuildArticleIndex() {
        articleSyncService.rebuildPublishedArticles();
        return Result.success(null);
    }
}
