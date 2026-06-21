package com.game.community.feign;

import com.game.community.model.base.Result;
import com.game.community.model.base.PageResult;
import com.game.community.model.entity.article.Article;
import com.game.community.model.entity.article.Category;
import com.game.community.model.vo.article.ArticleDetailVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "content-service", contextId = "contentFeignClient", path = "/feign/content")
public interface ContentFeignClient {

    @PostMapping("/articles/listByIds")
    Result<List<Article>> listArticlesByIds(@RequestBody List<Long> ids);

    @GetMapping("/articles/{articleId}/detail")
    Result<ArticleDetailVO> getArticleDetail(@PathVariable("articleId") Long articleId);

    @PostMapping("/articles/{articleId}/status")
    Result<Void> updateArticleStatus(@PathVariable("articleId") Long articleId,
                                     @RequestParam("status") Integer status);

    @GetMapping("/articles/published/page")
    Result<PageResult<Article>> listPublishedArticlesPage(@RequestParam(value = "page", defaultValue = "1") Integer page,
                                                          @RequestParam(value = "size", defaultValue = "50") Integer size);

    @GetMapping("/categories/enabled")
    Result<List<Category>> listEnabledCategories();

    @GetMapping("/categories/{categoryId}")
    Result<Category> getCategoryById(@PathVariable("categoryId") Long categoryId);

    @GetMapping("/articles/author/{authorId}/published")
    Result<List<Article>> listPublishedByAuthor(@PathVariable("authorId") Long authorId,
                                                @RequestParam(value = "size", defaultValue = "20") Integer size);

    @PostMapping("/articles/authors/published")
    Result<List<Article>> listPublishedByAuthors(@RequestBody List<Long> authorIds,
                                                 @RequestParam(value = "before", required = false) String before,
                                                 @RequestParam(value = "size", defaultValue = "20") Integer size);
}
