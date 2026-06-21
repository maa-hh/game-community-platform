package com.game.community.content.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.game.community.common.annotation.AdminCheck;
import com.game.community.common.annotation.LoginCheck;
import com.game.community.content.service.ArticleContentService;
import com.game.community.content.service.ArticleService;
import com.game.community.model.base.PageResult;
import com.game.community.model.base.Result;
import com.game.community.model.dto.article.ArticleDTO;
import com.game.community.model.entity.article.Article;
import com.game.community.model.mongo.ArticleContent;
import com.game.community.model.vo.article.ArticleDetailVO;
import com.game.community.utils.ThreadLocal.UserThreadLocal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 内容文章接口。
 */
@RestController
@RequestMapping("/article")
@RequiredArgsConstructor
public class ArticleController {

    private final ArticleService articleService;

    private final ArticleContentService articleContentService;

    @LoginCheck
    @PostMapping
    public Result<Long> saveArticle(@Valid @RequestBody ArticleDTO articleDTO) {
        return Result.success(articleService.saveArticle(articleDTO, UserThreadLocal.getUserId()));
    }

    @LoginCheck
    @PutMapping("/{id}")
    public Result<Long> updateArticle(@PathVariable("id") Long id, @Valid @RequestBody ArticleDTO articleDTO) {
        articleDTO.setId(id);
        return Result.success(articleService.saveArticle(articleDTO, UserThreadLocal.getUserId()));
    }

    @LoginCheck
    @DeleteMapping("/{id}")
    public Result<Void> deleteArticle(@PathVariable("id") Long id) {
        Article article = articleService.getById(id);
        if (article == null || !article.getUserId().equals(UserThreadLocal.getUserId())) {
            return Result.error("文章不存在或无权删除");
        }
        articleService.deleteArticle(id);
        return Result.success(null);
    }

    @GetMapping("/{id}")
    public Result<ArticleDetailVO> getArticleDetail(@PathVariable("id") Long id) {
        return Result.success(articleService.getArticleDetail(id));
    }

    @GetMapping("/{id}/content")
    public Result<ArticleContent> getArticleContent(@PathVariable("id") Long id) {
        return Result.success(articleContentService.getByArticleId(id));
    }

    @GetMapping("/page")
    public PageResult<Article> getArticlePage(@RequestParam(value = "page", defaultValue = "1") Integer page,
                                              @RequestParam(value = "size", defaultValue = "10") Integer size,
                                              @RequestParam(value = "categoryId", required = false) Long categoryId,
                                              @RequestParam(value = "status", required = false) Integer status) {
        Page<Article> result = articleService.getArticlePage(page, size, categoryId, status);
        return PageResult.of(result.getRecords(), result.getCurrent(), result.getSize(), result.getTotal());
    }

    @LoginCheck
    @GetMapping("/my")
    public Result<List<Article>> getMyArticles() {
        return Result.success(articleService.getUserArticles(UserThreadLocal.getUserId()));
    }

    @LoginCheck
    @GetMapping("/follow")
    public PageResult<Article> getFollowArticles(@RequestParam(value = "page", defaultValue = "1") Integer page,
                                                 @RequestParam(value = "size", defaultValue = "10") Integer size) {
        Page<Article> result = articleService.getFollowArticles(UserThreadLocal.getUserId(), page, size);
        return PageResult.of(result.getRecords(), result.getCurrent(), result.getSize(), result.getTotal());
    }

    @GetMapping("/latest")
    public Result<List<Article>> getLatestArticles(@RequestParam(value = "categoryId", required = false) Long categoryId,
                                                   @RequestParam(value = "size", defaultValue = "10") Integer size) {
        return Result.success(articleService.getLatestArticles(categoryId, size));
    }

    @GetMapping("/more")
    public Result<List<Article>> getMoreArticles(@RequestParam(value = "categoryId", required = false) Long categoryId,
                                                 @RequestParam("lastId") Long lastId,
                                                 @RequestParam(value = "size", defaultValue = "10") Integer size) {
        return Result.success(articleService.getMoreArticles(categoryId, lastId, size));
    }

    @LoginCheck
    @PutMapping("/{id}/publish")
    public Result<Void> publishArticle(@PathVariable("id") Long id) {
        articleService.updateArticleStatus(id, 1);
        return Result.success(null);
    }

    @LoginCheck
    @PutMapping("/{id}/unpublish")
    public Result<Void> unpublishArticle(@PathVariable("id") Long id) {
        articleService.updateArticleStatus(id, 3);
        return Result.success(null);
    }

    @GetMapping("/listPublished")
    public Result<List<Article>> listPublishedArticles() {
        return Result.success(articleService.listPublishedArticles());
    }

    @GetMapping("/listPublishedPage")
    public Result<PageResult<Article>> listPublishedArticlesPage(@RequestParam("page") Integer page,
                                                                 @RequestParam("size") Integer size) {
        return Result.success(articleService.listPublishedArticlesPage(page, size));
    }

    @PostMapping("/listByIds")
    public Result<List<Article>> listByIds(@RequestBody List<Long> ids) {
        return Result.success(articleService.listByIds(ids));
    }

    @GetMapping("/author/{authorId}/published")
    public Result<List<Article>> listPublishedByAuthor(@PathVariable("authorId") Long authorId,
                                                       @RequestParam(value = "size", defaultValue = "20") Integer size) {
        return Result.success(articleService.listPublishedByAuthor(authorId, size));
    }

    @PostMapping("/authors/published")
    public Result<List<Article>> listPublishedByAuthors(@RequestBody List<Long> authorIds,
                                                        @RequestParam(value = "before", required = false) String before,
                                                        @RequestParam(value = "size", defaultValue = "20") Integer size) {
        LocalDateTime beforeTime = before == null || before.isBlank() ? null : LocalDateTime.parse(before);
        return Result.success(articleService.listPublishedByAuthorsBefore(authorIds, beforeTime, size));
    }

    @AdminCheck
    @GetMapping("/admin/page")
    public PageResult<Article> getArticlePageAdmin(@RequestParam(value = "page", defaultValue = "1") Integer page,
                                                   @RequestParam(value = "size", defaultValue = "10") Integer size,
                                                   @RequestParam(value = "keyword", required = false) String keyword,
                                                   @RequestParam(value = "categoryId", required = false) Long categoryId,
                                                   @RequestParam(value = "status", required = false) Integer status,
                                                   @RequestParam(value = "authorId", required = false) Long authorId) {
        Page<Article> result = articleService.getArticlePageAdmin(page, size, keyword, categoryId, status, authorId);
        return PageResult.of(result.getRecords(), result.getCurrent(), result.getSize(), result.getTotal());
    }

    @AdminCheck
    @DeleteMapping("/admin/{articleId}")
    public Result<Void> deleteArticleAdmin(@PathVariable("articleId") Long articleId) {
        articleService.deleteArticleAdmin(articleId);
        return Result.success(null);
    }

    @AdminCheck
    @PutMapping("/admin/{articleId}/status")
    public Result<Void> updateArticleStatusAdmin(@PathVariable("articleId") Long articleId,
                                                 @RequestParam("status") Integer status) {
        articleService.updateArticleStatus(articleId, status);
        return Result.success(null);
    }

    @AdminCheck
    @GetMapping("/admin/count")
    public Result<Long> countArticle() {
        return Result.success(articleService.countArticle());
    }
}
