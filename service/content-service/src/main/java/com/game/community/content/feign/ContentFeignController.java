package com.game.community.content.feign;

import com.game.community.content.mapper.ArticleGameMapper;
import com.game.community.content.service.ArticleAsyncService;
import com.game.community.content.service.ArticleContentMigrationService;
import com.game.community.content.service.ArticleService;
import com.game.community.content.service.CategoryService;
import com.fasterxml.jackson.annotation.JsonView;
import com.game.community.model.base.PageResult;
import com.game.community.model.base.Result;
import com.game.community.model.json.ApiJsonViews;
import com.game.community.model.vo.article.ArticleDetailVO;
import com.game.community.model.vo.article.ArticleListVO;
import com.game.community.model.vo.article.CategoryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(value = "/feign/content", produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
@RequiredArgsConstructor
public class ContentFeignController {

    private final ArticleService articleService;
    private final CategoryService categoryService;
    private final ArticleAsyncService articleAsyncService;
    private final ArticleGameMapper articleGameMapper;
    private final ArticleContentMigrationService articleContentMigrationService;

    @PostMapping("/games/discuss-counts")
    public Result<Map<Long, Integer>> countPublishedDiscussByAppIds(@RequestBody List<Long> appIds) {
        if (appIds == null || appIds.isEmpty()) {
            return Result.success(Map.of());
        }
        Map<Long, Integer> result = new java.util.HashMap<>();
        for (Long appId : appIds) {
            if (appId != null) {
                result.put(appId, 0);
            }
        }
        for (var row : articleGameMapper.countPublishedGroupByAppIds(appIds)) {
            if (row.getAppId() != null) {
                result.put(row.getAppId(), row.getDiscussCount() == null ? 0 : row.getDiscussCount().intValue());
            }
        }
        return Result.success(result);
    }

    @PostMapping("/articles/listByIds")
    @JsonView(ApiJsonViews.Internal.class)
    public Result<List<ArticleListVO>> listArticlesByIds(@RequestBody List<Long> ids) {
        return articleService.queryByIds(ids);
    }

    @PostMapping("/articles/public/list")
    @JsonView(ApiJsonViews.Internal.class)
    public Result<List<ArticleListVO>> listArticlesByPublicIds(@RequestBody List<String> publicIds) {
        return articleService.queryByPublicIds(publicIds);
    }

    @GetMapping("/articles/{articleId}/detail")
    @JsonView(ApiJsonViews.Internal.class)
    public Result<ArticleDetailVO> getArticleDetail(@PathVariable("articleId") Long articleId) {
        return articleService.queryArticleDetailInternal(articleId);
    }

    @GetMapping("/articles/public/{publicId}")
    @JsonView(ApiJsonViews.Internal.class)
    public Result<ArticleListVO> getArticleByPublicId(@PathVariable("publicId") String publicId) {
        return articleService.queryByPublicIds(List.of(publicId)).getData().stream().findFirst()
                .map(Result::success)
                .orElseGet(() -> Result.success(null));
    }

    @PostMapping("/articles/{articleId}/status")
    public Result<Void> updateArticleStatus(@PathVariable("articleId") Long articleId,
                                          @RequestParam("status") Integer status) {
        return articleService.updateArticleStatusForFeign(articleId, status);
    }

    @GetMapping("/articles/published/page")
    @JsonView(ApiJsonViews.Internal.class)
    public Result<PageResult<ArticleListVO>> listPublishedArticlesPage(
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "size", defaultValue = "50") Integer size) {
        return articleService.queryPublishedPageForFeign(page, size);
    }

    @PostMapping("/articles/categoryIds")
    public Result<Map<Long, List<Long>>> listCategoryIdsByArticleIds(@RequestBody List<Long> articleIds) {
        return articleService.queryCategoryIdsByArticleIds(articleIds);
    }

    @GetMapping("/categories/enabled")
    public Result<List<CategoryVO>> listEnabledCategories() {
        return categoryService.listEnabledApi();
    }

    @GetMapping("/categories/{categoryId}")
    public Result<CategoryVO> getCategoryById(@PathVariable("categoryId") Long categoryId) {
        return categoryService.getCategoryByIdApi(categoryId);
    }

    @GetMapping("/articles/author/{authorId}/published")
    @JsonView(ApiJsonViews.Internal.class)
    public Result<List<ArticleListVO>> listPublishedByAuthor(@PathVariable("authorId") Long authorId,
                                                            @RequestParam(value = "size", defaultValue = "20") Integer size) {
        return articleService.queryPublishedByAuthor(authorId, size);
    }

    @PostMapping("/articles/authors/published")
    @JsonView(ApiJsonViews.Internal.class)
    public Result<List<ArticleListVO>> listPublishedByAuthors(@RequestBody List<Long> authorIds,
                                                              @RequestParam(value = "before", required = false) String before,
                                                              @RequestParam(value = "beforeArticleId", required = false) Long beforeArticleId,
                                                              @RequestParam(value = "size", defaultValue = "20") Integer size) {
        return articleService.queryPublishedByAuthors(authorIds, before, beforeArticleId, size);
    }

    @PostMapping("/articles/{articleId}/manual-audit/approve")
    public Result<Void> approveArticleManualAudit(@PathVariable("articleId") Long articleId) {
        articleAsyncService.publishAfterManualApproval(articleId);
        return Result.success(null);
    }

    @PostMapping("/articles/migrate-image-text-content")
    public Result<Integer> migrateImageTextContent() {
        return Result.success(articleContentMigrationService.migrateLegacyImageTextContent());
    }

    @PostMapping("/articles/{articleId}/manual-audit/reject")
    public Result<Void> rejectArticleManualAudit(@PathVariable("articleId") Long articleId,
                                                 @RequestParam(value = "reason", required = false) String reason) {
        articleAsyncService.rejectAfterManualApproval(articleId, reason);
        return Result.success(null);
    }
}
