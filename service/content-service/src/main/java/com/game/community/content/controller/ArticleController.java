package com.game.community.content.controller;

import com.game.community.common.annotation.AdminCheck;
import com.game.community.common.annotation.LoginCheck;
import com.fasterxml.jackson.annotation.JsonView;
import com.game.community.content.service.ArticleByGameService;
import com.game.community.content.service.ArticleService;
import com.game.community.model.base.PageResult;
import com.game.community.model.base.Result;
import com.game.community.model.dto.article.ArticleDTO;
import com.game.community.model.json.ApiJsonViews;
import com.game.community.model.vo.article.ArticleContentVO;
import com.game.community.model.vo.article.ArticleDetailVO;
import com.game.community.model.vo.article.ArticleListVO;
import com.game.community.model.vo.article.ArticleProgressVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 内容文章接口。
 */
@RestController
@RequestMapping("/article")
@RequiredArgsConstructor
public class ArticleController {

    private final ArticleService articleService;
    private final ArticleByGameService articleByGameService;

    @GetMapping("/by-game/{appId}")
    @JsonView(ApiJsonViews.Public.class)
    public PageResult<ArticleListVO> listByGame(@PathVariable("appId") Long appId,
                                                @RequestParam(value = "page", defaultValue = "1") Long page,
                                                @RequestParam(value = "size", defaultValue = "10") Long size) {
        return articleByGameService.pageByGame(appId, page, size);
    }

    @LoginCheck
    @PostMapping
    public Result<String> saveArticle(@Valid @RequestBody ArticleDTO articleDTO) {
        return articleService.saveArticleForCurrentUser(articleDTO);
    }

    @LoginCheck
    @PutMapping("/{id}")
    public Result<String> updateArticle(@PathVariable("id") String publicId,
                                        @Valid @RequestBody ArticleDTO articleDTO) {
        return articleService.updateArticleForCurrentUser(publicId, articleDTO);
    }

    @LoginCheck
    @DeleteMapping("/{id}")
    public Result<Void> deleteArticle(@PathVariable("id") String publicId) {
        return articleService.deleteArticleForCurrentUser(publicId);
    }

    @GetMapping("/{id}")
    @JsonView(ApiJsonViews.Public.class)
    public Result<ArticleDetailVO> getArticleDetail(@PathVariable("id") String publicId) {
        return articleService.queryArticleDetail(publicId);
    }

    @LoginCheck
    @GetMapping("/{id}/mine")
    @JsonView(ApiJsonViews.Public.class)
    public Result<ArticleDetailVO> getMyArticleDetail(@PathVariable("id") String publicId) {
        return articleService.queryArticleDetailForOwner(publicId);
    }

    @GetMapping("/{id}/content")
    public Result<ArticleContentVO> getArticleContent(@PathVariable("id") String publicId) {
        return articleService.queryArticleContent(publicId);
    }

    @GetMapping("/page")
    @JsonView(ApiJsonViews.Public.class)
    public PageResult<ArticleListVO> getArticlePage(@RequestParam(value = "page", defaultValue = "1") Integer page,
                                                    @RequestParam(value = "size", defaultValue = "10") Integer size,
                                                    @RequestParam(value = "categoryId", required = false) Long categoryId,
                                                    @RequestParam(value = "status", required = false) Integer status) {
        return articleService.queryArticlePage(page, size, categoryId, status);
    }

    @LoginCheck
    @GetMapping("/my")
    @JsonView(ApiJsonViews.Public.class)
    public PageResult<ArticleListVO> getMyArticles(@RequestParam(value = "page", defaultValue = "1") Integer page,
                                                   @RequestParam(value = "size", defaultValue = "20") Integer size,
                                                   @RequestParam(value = "tab", required = false) String tab) {
        return articleService.queryMyArticlesPage(page, size, tab);
    }

    @LoginCheck
    @GetMapping("/follow")
    @JsonView(ApiJsonViews.Public.class)
    public PageResult<ArticleListVO> getFollowArticles(@RequestParam(value = "page", defaultValue = "1") Integer page,
                                                       @RequestParam(value = "size", defaultValue = "10") Integer size) {
        return articleService.queryFollowArticles(page, size);
    }

    @GetMapping("/latest")
    @JsonView(ApiJsonViews.Public.class)
    public Result<List<ArticleListVO>> getLatestArticles(@RequestParam(value = "categoryId", required = false) Long categoryId,
                                                         @RequestParam(value = "size", defaultValue = "10") Integer size) {
        return articleService.queryLatestArticles(categoryId, size);
    }

    @GetMapping("/more")
    @JsonView(ApiJsonViews.Public.class)
    public Result<List<ArticleListVO>> getMoreArticles(@RequestParam(value = "categoryId", required = false) Long categoryId,
                                                     @RequestParam("lastId") String lastPublicId,
                                                     @RequestParam(value = "size", defaultValue = "10") Integer size) {
        return articleService.queryMoreArticles(categoryId, lastPublicId, size);
    }

    @LoginCheck
    @GetMapping("/{id}/progress")
    public Result<ArticleProgressVO> getArticleProgress(@PathVariable("id") String publicId) {
        return articleService.queryArticleProgress(publicId);
    }

    @LoginCheck
    @PutMapping("/{id}/publish")
    public Result<Void> publishArticle(@PathVariable("id") String publicId) {
        return articleService.submitPublish(publicId);
    }

    @LoginCheck
    @PutMapping("/{id}/unpublish")
    public Result<Void> unpublishArticle(@PathVariable("id") String publicId) {
        return articleService.submitUnpublish(publicId);
    }

    @GetMapping("/listPublished")
    @JsonView(ApiJsonViews.Public.class)
    public Result<List<ArticleListVO>> listPublishedArticles() {
        return articleService.queryPublishedList();
    }

    @GetMapping("/listPublishedPage")
    @JsonView(ApiJsonViews.Public.class)
    public Result<PageResult<ArticleListVO>> listPublishedArticlesPage(@RequestParam("page") Integer page,
                                                                     @RequestParam("size") Integer size) {
        return articleService.queryPublishedPage(page, size);
    }

    @PostMapping("/listByIds")
    @JsonView(ApiJsonViews.Public.class)
    public Result<List<ArticleListVO>> listByIds(@RequestBody List<String> publicIds) {
        return articleService.queryByPublicIds(publicIds);
    }

    @GetMapping("/author/{authorId}/published")
    @JsonView(ApiJsonViews.Public.class)
    public Result<List<ArticleListVO>> listPublishedByAuthor(@PathVariable("authorId") Long authorId,
                                                           @RequestParam(value = "size", defaultValue = "20") Integer size) {
        return articleService.queryPublishedByAuthor(authorId, size);
    }

    @GetMapping("/author/account/{accountId}/published")
    @JsonView(ApiJsonViews.Public.class)
    public Result<List<ArticleListVO>> listPublishedByAccount(@PathVariable("accountId") Long accountId,
                                                              @RequestParam(value = "size", defaultValue = "20") Integer size) {
        return articleService.queryPublishedByAccountId(accountId, size);
    }

    @PostMapping("/authors/published")
    @JsonView(ApiJsonViews.Public.class)
    public Result<List<ArticleListVO>> listPublishedByAuthors(@RequestBody List<Long> authorIds,
                                                              @RequestParam(value = "before", required = false) String before,
                                                              @RequestParam(value = "beforeArticleId", required = false) Long beforeArticleId,
                                                              @RequestParam(value = "size", defaultValue = "20") Integer size) {
        return articleService.queryPublishedByAuthors(authorIds, before, beforeArticleId, size);
    }

    @AdminCheck
    @GetMapping("/admin/page")
    @JsonView(ApiJsonViews.Public.class)
    public PageResult<ArticleListVO> getArticlePageAdmin(@RequestParam(value = "page", defaultValue = "1") Integer page,
                                                       @RequestParam(value = "size", defaultValue = "10") Integer size,
                                                       @RequestParam(value = "keyword", required = false) String keyword,
                                                       @RequestParam(value = "categoryId", required = false) Long categoryId,
                                                       @RequestParam(value = "status", required = false) Integer status,
                                                       @RequestParam(value = "authorId", required = false) Long authorId) {
        return articleService.queryArticlePageAdmin(page, size, keyword, categoryId, status, authorId);
    }

    @AdminCheck
    @DeleteMapping("/admin/{articleId}")
    public Result<Void> deleteArticleAdmin(@PathVariable("articleId") Long articleId) {
        return articleService.deleteArticleAdminOp(articleId);
    }

    @AdminCheck
    @PutMapping("/admin/{articleId}/status")
    public Result<Void> updateArticleStatusAdmin(@PathVariable("articleId") Long articleId,
                                                 @RequestParam("status") Integer status) {
        return articleService.updateArticleStatusAdmin(articleId, status);
    }

    @AdminCheck
    @GetMapping("/admin/count")
    public Result<Long> countArticle() {
        return articleService.countArticles();
    }
}
