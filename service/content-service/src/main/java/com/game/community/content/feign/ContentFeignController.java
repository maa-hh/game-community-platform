package com.game.community.content.feign;

import com.game.community.content.service.ArticleService;
import com.game.community.content.service.CategoryService;
import com.game.community.model.base.PageResult;
import com.game.community.model.base.Result;
import com.game.community.model.entity.article.Article;
import com.game.community.model.entity.article.Category;
import com.game.community.model.vo.article.ArticleDetailVO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping(value = "/feign/content", produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
@RequiredArgsConstructor
public class ContentFeignController {

    private final ArticleService articleService;
    private final CategoryService categoryService;

    @PostMapping("/articles/listByIds")
    public Result<List<Article>> listArticlesByIds(@RequestBody List<Long> ids) {
        return Result.success(articleService.listByIds(ids));
    }

    @GetMapping("/articles/{articleId}/detail")
    public Result<ArticleDetailVO> getArticleDetail(@PathVariable("articleId") Long articleId) {
        return Result.success(articleService.getArticleDetail(articleId));
    }

    @PostMapping("/articles/{articleId}/status")
    public Result<Void> updateArticleStatus(@PathVariable("articleId") Long articleId,
                                            @RequestParam("status") Integer status) {
        articleService.updateArticleStatus(articleId, status);
        return Result.success(null);
    }

    @GetMapping("/articles/published/page")
    public Result<PageResult<Article>> listPublishedArticlesPage(@RequestParam(value = "page", defaultValue = "1") Integer page,
                                                                 @RequestParam(value = "size", defaultValue = "50") Integer size) {
        return Result.success(articleService.listPublishedArticlesPage(page, size));
    }

    @GetMapping("/categories/enabled")
    public Result<List<Category>> listEnabledCategories() {
        return Result.success(categoryService.listEnabled());
    }

    @GetMapping("/categories/{categoryId}")
    public Result<Category> getCategoryById(@PathVariable("categoryId") Long categoryId) {
        return Result.success(categoryService.getById(categoryId));
    }

    @GetMapping("/articles/author/{authorId}/published")
    public Result<List<Article>> listPublishedByAuthor(@PathVariable("authorId") Long authorId,
                                                       @RequestParam(value = "size", defaultValue = "20") Integer size) {
        return Result.success(articleService.listPublishedByAuthor(authorId, size));
    }

    @PostMapping("/articles/authors/published")
    public Result<List<Article>> listPublishedByAuthors(@RequestBody List<Long> authorIds,
                                                        @RequestParam(value = "before", required = false) String before,
                                                        @RequestParam(value = "size", defaultValue = "20") Integer size) {
        LocalDateTime beforeTime = before == null || before.isBlank() ? null : LocalDateTime.parse(before);
        return Result.success(articleService.listPublishedByAuthorsBefore(authorIds, beforeTime, size));
    }
}
