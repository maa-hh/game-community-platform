package com.game.community.feign;

import com.game.community.model.base.Result;
import com.game.community.model.base.PageResult;
import com.game.community.model.vo.article.ArticleDetailVO;
import com.game.community.model.vo.article.ArticleListVO;
import com.game.community.model.vo.article.CategoryVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

@FeignClient(name = "content-service", contextId = "contentFeignClient", path = "/feign/content")
public interface ContentFeignClient {

    @PostMapping("/articles/listByIds")
    Result<List<ArticleListVO>> listArticlesByIds(@RequestBody List<Long> ids);

    @PostMapping("/articles/public/list")
    Result<List<ArticleListVO>> listArticlesByPublicIds(@RequestBody List<String> publicIds);

    @GetMapping("/articles/{articleId}/detail")
    Result<ArticleDetailVO> getArticleDetail(@PathVariable("articleId") Long articleId);

    @GetMapping("/articles/public/{publicId}")
    Result<ArticleListVO> getArticleByPublicId(@PathVariable("publicId") String publicId);

    @PostMapping("/articles/{articleId}/status")
    Result<Void> updateArticleStatus(@PathVariable("articleId") Long articleId,
                                     @RequestParam("status") Integer status);

    @PostMapping("/articles/{articleId}/manual-audit/approve")
    Result<Void> approveArticleManualAudit(@PathVariable("articleId") Long articleId);

    @PostMapping("/articles/{articleId}/manual-audit/reject")
    Result<Void> rejectArticleManualAudit(@PathVariable("articleId") Long articleId,
                                          @RequestParam(value = "reason", required = false) String reason);

    @GetMapping("/articles/published/page")
    Result<PageResult<ArticleListVO>> listPublishedArticlesPage(@RequestParam(value = "page", defaultValue = "1") Integer page,
                                                                @RequestParam(value = "size", defaultValue = "50") Integer size);

    @PostMapping("/articles/categoryIds")
    Result<Map<Long, List<Long>>> listCategoryIdsByArticleIds(@RequestBody List<Long> articleIds);

    @GetMapping("/categories/enabled")
    Result<List<CategoryVO>> listEnabledCategories();

    @GetMapping("/categories/{categoryId}")
    Result<CategoryVO> getCategoryById(@PathVariable("categoryId") Long categoryId);

    @GetMapping("/articles/author/{authorId}/published")
    Result<List<ArticleListVO>> listPublishedByAuthor(@PathVariable("authorId") Long authorId,
                                                      @RequestParam(value = "size", defaultValue = "20") Integer size);

    @PostMapping("/articles/authors/published")
    Result<List<ArticleListVO>> listPublishedByAuthors(@RequestBody List<Long> authorIds,
                                                       @RequestParam(value = "before", required = false) String before,
                                                       @RequestParam(value = "beforeArticleId", required = false) Long beforeArticleId,
                                                       @RequestParam(value = "size", defaultValue = "20") Integer size);

    @PostMapping("/games/discuss-counts")
    Result<Map<Long, Integer>> countPublishedDiscussByAppIds(@RequestBody List<Long> appIds);
}
